package com.mocharealm.accompanist.lyrics.text

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

actual class SdfAtlasManager actual constructor(
    atlasWidth: Int,
    atlasHeight: Int
) {
    actual val width: Int = atlasWidth
    actual val height: Int = atlasHeight
    
    // The atlas bitmap - ARGB_8888 format
    private val atlasBitmap: Bitmap = Bitmap.createBitmap(atlasWidth, atlasHeight, Bitmap.Config.ARGB_8888)
    private var atlasImageBitmap: ImageBitmap? = null
    private var isDirty = true
    private var hasAnyData = false
    
    // SDF rendering parameters
    private val sdfThreshold = 0.5f  // Edge threshold
    private val sdfSmoothing = 0.02f  // Anti-aliasing width (smaller = sharper edges)
    
    actual fun updateAtlas(uploads: List<GlyphUpload>) {
        if (uploads.isEmpty()) return
        
        for (upload in uploads) {
            if (upload.width <= 0 || upload.height <= 0) continue
            if (upload.x + upload.width > width || upload.y + upload.height > height) continue
            
            // Convert RGBA byte array to ARGB int array for Android Bitmap
            // Apply SDF thresholding to sharpen the text
            val pixels = IntArray(upload.width * upload.height)
            val data = upload.data
            
            for (i in pixels.indices) {
                val offset = i * 4
                if (offset + 3 < data.size) {
                    val r = data[offset].toInt() and 0xFF
                    val g = data[offset + 1].toInt() and 0xFF
                    val b = data[offset + 2].toInt() and 0xFF
                    val sdfValue = (data[offset + 3].toInt() and 0xFF) / 255f
                    
                    // Apply smoothstep for crisp edges with anti-aliasing
                    val alpha = smoothstep(
                        sdfThreshold - sdfSmoothing,
                        sdfThreshold + sdfSmoothing,
                        sdfValue
                    )
                    val a = (alpha * 255f).toInt().coerceIn(0, 255)
                    
                    // ARGB format for Android
                    pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            
            // Set pixels to the atlas bitmap
            atlasBitmap.setPixels(
                pixels,
                0,
                upload.width,
                upload.x,
                upload.y,
                upload.width,
                upload.height
            )
            hasAnyData = true
        }
        
        isDirty = true
    }
    
    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    
    private fun ensureImageBitmap() {
        if (isDirty || atlasImageBitmap == null) {
            atlasImageBitmap = atlasBitmap.asImageBitmap()
            isDirty = false
        }
    }
    
    actual fun DrawScope.drawGlyph(
        atlasRect: Rect,
        destOffset: Offset,
        destSize: Size,
        color: Color
    ) {
        if (!hasAnyData) return
        if (atlasRect.width <= 0 || atlasRect.height <= 0) return
        
        ensureImageBitmap()
        val imageBitmap = atlasImageBitmap ?: return
        
        // Draw the glyph with color tinting using SrcIn blend mode
        // SrcIn uses the destination alpha (from atlas) and source color
        drawImage(
            image = imageBitmap,
            srcOffset = IntOffset(atlasRect.left.toInt(), atlasRect.top.toInt()),
            srcSize = IntSize(atlasRect.width.toInt(), atlasRect.height.toInt()),
            dstOffset = IntOffset(destOffset.x.toInt(), destOffset.y.toInt()),
            dstSize = IntSize(destSize.width.toInt(), destSize.height.toInt()),
            colorFilter = ColorFilter.tint(color, BlendMode.SrcIn)
        )
    }
    
    actual fun isReady(): Boolean = hasAnyData
    
    actual fun destroy() {
        atlasBitmap.recycle()
        atlasImageBitmap = null
        hasAnyData = false
    }
}
