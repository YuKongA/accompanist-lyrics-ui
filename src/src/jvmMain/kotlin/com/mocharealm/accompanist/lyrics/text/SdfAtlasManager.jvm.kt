package com.mocharealm.accompanist.lyrics.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.awt.image.BufferedImage

actual class SdfAtlasManager actual constructor(
    atlasWidth: Int,
    atlasHeight: Int
) {
    actual val width: Int = atlasWidth
    actual val height: Int = atlasHeight
    
    // The atlas as a BufferedImage - TYPE_INT_ARGB format
    private val atlasImage: BufferedImage = BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB)
    private var atlasImageBitmap: ImageBitmap? = null
    private var isDirty = true
    private var hasAnyData = false
    
    // Native text engine for font loading
    private val nativeEngine = NativeTextEngine()
    
    init {
        nativeEngine.init(atlasWidth, atlasHeight)
    }
    
    // SDF rendering parameters
    private val sdfThreshold = 0.5f  // Edge threshold
    private val sdfSmoothing = 0.02f  // Anti-aliasing width (smaller = sharper edges)
    
    actual fun loadFont(fontBytes: ByteArray) {
        nativeEngine.loadFont(fontBytes)
    }
    
    actual fun loadFallbackFont(fontBytes: ByteArray) {
        nativeEngine.loadFallbackFont(fontBytes)
    }
    
    actual fun clearFallbackFonts() {
        nativeEngine.clearFallbackFonts()
    }
    
    actual fun updateAtlas(uploads: List<GlyphUpload>) {
        if (uploads.isEmpty()) return
        
        for (upload in uploads) {
            if (upload.width <= 0 || upload.height <= 0) continue
            if (upload.x + upload.width > width || upload.y + upload.height > height) continue
            
            // Convert RGBA byte array to ARGB int array
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
                    
                    // ARGB format for Java AWT
                    pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            
            // Set pixels to the atlas image
            atlasImage.setRGB(
                upload.x,
                upload.y,
                upload.width,
                upload.height,
                pixels,
                0,
                upload.width
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
            atlasImageBitmap = atlasImage.toComposeImageBitmap()
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
        atlasImageBitmap = null
        hasAnyData = false
        nativeEngine.destroy()
    }
}

/**
 * Convert BufferedImage to Compose ImageBitmap
 */
private fun BufferedImage.toComposeImageBitmap(): ImageBitmap {
    val pixels = IntArray(width * height)
    getRGB(0, 0, width, height, pixels, 0, width)
    
    // Convert IntArray (ARGB) to ByteArray (BGRA for Skia N32)
    val byteArray = ByteArray(pixels.size * 4)
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val offset = i * 4
        // Skia N32 on little-endian is BGRA
        byteArray[offset] = (pixel and 0xFF).toByte()           // B
        byteArray[offset + 1] = ((pixel shr 8) and 0xFF).toByte()  // G
        byteArray[offset + 2] = ((pixel shr 16) and 0xFF).toByte() // R
        byteArray[offset + 3] = ((pixel shr 24) and 0xFF).toByte() // A
    }
    
    return org.jetbrains.skia.Image.makeFromBitmap(
        org.jetbrains.skia.Bitmap().apply {
            allocPixels(org.jetbrains.skia.ImageInfo.makeN32Premul(this@toComposeImageBitmap.width, this@toComposeImageBitmap.height))
            installPixels(byteArray)
        }
    ).toComposeImageBitmap()
}
