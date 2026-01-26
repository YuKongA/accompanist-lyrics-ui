
use std::collections::HashMap;

#[derive(Clone, Copy, Debug)]
pub struct Rect {
    pub x: u32,
    pub y: u32,
    pub width: u32,
    pub height: u32,
}

/// Cached glyph information including atlas rect and bearing offsets
#[derive(Clone, Copy, Debug)]
pub struct GlyphInfo {
    pub rect: Rect,
    pub x_bearing: f32,
    pub y_bearing: f32,
}

/// Cache key for glyphs including weight for variable font support
type GlyphCacheKey = (usize, u16, u32, u32); // (font_id, glyph_id, size_px, weight)

pub struct AtlasManager {
    pub width: u32,
    pub height: u32,
    shelves: Vec<Shelf>,
    // Mapping from (FontID, GlyphID, FontSize, Weight) -> GlyphInfo
    glyph_cache: HashMap<GlyphCacheKey, GlyphInfo>,
}

struct Shelf {
    y: u32,
    height: u32,
    current_x: u32,
}

impl AtlasManager {
    pub fn new(width: u32, height: u32) -> Self {
        Self {
            width,
            height,
            shelves: Vec::new(),
            glyph_cache: HashMap::new(),
        }
    }

    /// Get glyph info with weight support (for variable fonts)
    pub fn get_glyph_info_with_weight(&self, font_id: usize, glyph_id: u16, size_px: u32, weight: u32) -> Option<GlyphInfo> {
        self.glyph_cache.get(&(font_id, glyph_id, size_px, weight)).cloned()
    }
    
    /// Legacy method - uses default weight of 400
    #[allow(dead_code)]
    pub fn get_glyph_info(&self, font_id: usize, glyph_id: u16, size_px: u32) -> Option<GlyphInfo> {
        self.get_glyph_info_with_weight(font_id, glyph_id, size_px, 400)
    }
    
    /// Legacy method for backward compatibility
    #[allow(dead_code)]
    pub fn get_glyph_rect(&self, font_id: usize, glyph_id: u16, size_px: u32) -> Option<Rect> {
        self.get_glyph_info(font_id, glyph_id, size_px).map(|info| info.rect)
    }

    pub fn allocate(&mut self, width: u32, height: u32) -> Option<Rect> {
        // Try to add to existing shelves
        for shelf in &mut self.shelves {
            if shelf.height >= height && (self.width - shelf.current_x) >= width {
                let rect = Rect {
                    x: shelf.current_x,
                    y: shelf.y,
                    width,
                    height,
                };
                shelf.current_x += width;
                return Some(rect);
            }
        }

        // Create a new shelf
        // Find y position for new shelf. It's the bottom of the last shelf, or 0.
        let start_y = if let Some(last) = self.shelves.last() {
            last.y + last.height
        } else {
            0
        };

        if start_y + height <= self.height {
            let mut new_shelf = Shelf {
                y: start_y,
                height,
                current_x: 0,
            };
            let rect = Rect {
                x: 0,
                y: start_y,
                width,
                height,
            };
            new_shelf.current_x += width;
            self.shelves.push(new_shelf);
            return Some(rect);
        }

        // Full
        None
    }

    /// Cache glyph with weight support (for variable fonts)
    pub fn cache_glyph_with_weight(&mut self, font_id: usize, glyph_id: u16, size_px: u32, weight: u32, info: GlyphInfo) {
        self.glyph_cache.insert((font_id, glyph_id, size_px, weight), info);
    }
    
    /// Legacy method - uses default weight of 400
    #[allow(dead_code)]
    pub fn cache_glyph(&mut self, font_id: usize, glyph_id: u16, size_px: u32, info: GlyphInfo) {
        self.cache_glyph_with_weight(font_id, glyph_id, size_px, 400, info);
    }
}
