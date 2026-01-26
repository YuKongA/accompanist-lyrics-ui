
use crate::atlas::{AtlasManager, Rect};
use crate::font::FontWrapper;
use rustybuzz::{Face, UnicodeBuffer};

use serde::{Serialize, Deserialize};

#[derive(Serialize, Deserialize)]
pub struct LayoutResult {
    pub glyph_count: usize,
    // Flat arrays for JNI transfer
    pub glyph_ids: Vec<u16>, 
    pub positions: Vec<f32>, // x, y interleaved (relative to baseline)
    pub atlas_rects: Vec<f32>, // u, v, w, h in atlas
    pub glyph_offsets: Vec<f32>, // x_offset, y_offset interleaved (bearing from glyph origin to bitmap top-left)
    pub total_width: f32,
    pub total_height: f32,
    pub ascent: f32,
    pub descent: f32,
}

#[derive(Clone)]
pub struct PendingUpload {
    pub x: u32,
    pub y: u32,
    pub width: u32,
    pub height: u32,
    pub data: Vec<u8>, // RGBA data
}

pub struct TextEngine {
    atlas: AtlasManager,
    font: Option<FontWrapper>,
    font_data: Vec<u8>, // We own the bytes
    pending_uploads: Vec<PendingUpload>,
    pub atlas_width: u32,
    pub atlas_height: u32,
}

impl TextEngine {
    pub fn new(atlas_width: u32, atlas_height: u32) -> Self {
        Self {
            atlas: AtlasManager::new(atlas_width, atlas_height),
            font: None,
            font_data: Vec::new(),
            pending_uploads: Vec::new(),
            atlas_width,
            atlas_height,
        }
    }

    pub fn load_font(&mut self, font_bytes: Vec<u8>) {
        // Init FontWrapper (fontdue)
        if let Some(wrapper) = FontWrapper::from_bytes(&font_bytes, 0) {
            self.font = Some(wrapper);
        }
         self.font_data = font_bytes;
    }
    
    pub fn get_pending_uploads(&mut self) -> Vec<PendingUpload> {
        std::mem::take(&mut self.pending_uploads)
    }
    
    pub fn has_pending_uploads(&self) -> bool {
        !self.pending_uploads.is_empty()
    }
    
    pub fn get_atlas_size(&self) -> (u32, u32) {
        (self.atlas_width, self.atlas_height)
    }
    
    /// Clear all cached data and reset the engine.
    /// Call this when switching fonts or to free memory.
    pub fn clear(&mut self) {
        self.atlas = AtlasManager::new(self.atlas_width, self.atlas_height);
        self.font = None;
        self.font_data = Vec::new();
        self.pending_uploads.clear();
    }

    pub fn process_text(&mut self, text: &str, size_px: f32, weight: f32) -> LayoutResult {
        if self.font_data.is_empty() {
             return LayoutResult {
                 glyph_count: 0,
                 glyph_ids: vec![],
                 positions: vec![],
                 atlas_rects: vec![],
                 glyph_offsets: vec![],
                 total_width: 0.0,
                 total_height: 0.0,
                 ascent: 0.0,
                 descent: 0.0,
             };
        }

        let mut face = Face::from_slice(&self.font_data, 0).unwrap(); // fast
        
        // Set font weight variation for variable fonts
        // This ensures shaping uses the correct glyph metrics for the specified weight
        face.set_variations(&[rustybuzz::Variation {
            tag: rustybuzz::ttf_parser::Tag::from_bytes(b"wght"),
            value: weight,
        }]);
        
        let mut buffer = UnicodeBuffer::new();
        buffer.push_str(text);
        
        let glyph_buffer = rustybuzz::shape(&face, &[], buffer);
        let glyph_infos = glyph_buffer.glyph_infos();
        let glyph_positions = glyph_buffer.glyph_positions();

        let mut ids = Vec::with_capacity(glyph_infos.len());
        let mut pos = Vec::with_capacity(glyph_infos.len() * 2);
        let mut rects = Vec::with_capacity(glyph_infos.len() * 4);
        let mut offsets = Vec::with_capacity(glyph_infos.len() * 2);

        let mut x_cursor = 0.0;
        let mut y_cursor = 0.0;
        
        // Scale factor from font units to pixels
        let units_per_em = face.units_per_em() as f32;
        let scale = size_px / units_per_em;
        
        // Quantize weight to reduce cache fragmentation (round to nearest 100)
        let weight_key = ((weight / 100.0).round() * 100.0) as u32;

        for (info, gp) in glyph_infos.iter().zip(glyph_positions.iter()) {
            let glyph_id = info.glyph_id as u16;
            
            // Get glyph info (rect + bearing) from cache or generate new
            // Cache key includes weight for variable font support
            let glyph_info = if let Some(cached) = self.atlas.get_glyph_info_with_weight(0, glyph_id, size_px as u32, weight_key) {
                cached
            } else {
                // Generate and cache
                if let Some(font) = &mut self.font {
                    let (bitmap, w, h, xmin, ymin) = font.generate_sdf(glyph_id, size_px, weight);
                    
                    if w > 0 && h > 0 {
                         if let Some(alloc_rect) = self.atlas.allocate(w, h) {
                            // Store the bitmap data for later upload to GPU
                            self.pending_uploads.push(PendingUpload {
                                x: alloc_rect.x,
                                y: alloc_rect.y,
                                width: w,
                                height: h,
                                data: bitmap,
                            });
                            
                            let info = crate::atlas::GlyphInfo {
                                rect: alloc_rect,
                                x_bearing: xmin,
                                y_bearing: ymin,
                            };
                            self.atlas.cache_glyph_with_weight(0, glyph_id, size_px as u32, weight_key, info);
                            info
                        } else {
                             // Atlas full!
                             crate::atlas::GlyphInfo {
                                 rect: Rect { x: 0, y: 0, width: 0, height: 0 },
                                 x_bearing: 0.0,
                                 y_bearing: 0.0,
                             }
                        }
                    } else {
                         crate::atlas::GlyphInfo {
                             rect: Rect { x: 0, y: 0, width: 0, height: 0 },
                             x_bearing: xmin,
                             y_bearing: ymin,
                         }
                    }
                } else {
                     crate::atlas::GlyphInfo {
                         rect: Rect { x: 0, y: 0, width: 0, height: 0 },
                         x_bearing: 0.0,
                         y_bearing: 0.0,
                     }
                }
            };

            ids.push(glyph_id);
            
            // Calculate final position (relative to baseline)
            let x_pos = x_cursor + (gp.x_offset as f32 * scale);
            let y_pos = y_cursor + (gp.y_offset as f32 * scale);
            
            pos.push(x_pos);
            pos.push(y_pos);
            
            // Store bearing offsets for correct glyph positioning
            offsets.push(glyph_info.x_bearing);
            offsets.push(glyph_info.y_bearing);
            
            x_cursor += gp.x_advance as f32 * scale;
            y_cursor += gp.y_advance as f32 * scale;
            
            rects.push(glyph_info.rect.x as f32);
            rects.push(glyph_info.rect.y as f32);
            rects.push(glyph_info.rect.width as f32);
            rects.push(glyph_info.rect.height as f32);
        }

        LayoutResult {
            glyph_count: glyph_infos.len(),
            glyph_ids: ids,
            positions: pos,
            atlas_rects: rects,
            glyph_offsets: offsets,
            total_width: x_cursor,
            total_height: face.height() as f32 * scale,
            ascent: face.ascender() as f32 * scale,
            descent: face.descender() as f32 * scale,
        }
    }
}
