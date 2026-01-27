package com.mocharealm.accompanist.lyrics.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
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
    
    // The atlas as a BufferedImage - TYPE_INT_ARGB format (for normal text rendering)
    private val atlasImage: BufferedImage = BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB)
    private var atlasImageBitmap: ImageBitmap? = null
    
    // Shadow atlas - uses lower threshold for softer, larger glow
    private val shadowAtlasImage: BufferedImage = BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB)
    private var shadowAtlasImageBitmap: ImageBitmap? = null
    
    private var isDirty = true
    private var hasAnyData = false
    
    // SDF rendering parameters for normal text
    private val sdfThreshold = 0.7f  // Edge threshold (keep at 0.7 as requested)
    private val sdfSmoothing = 0.02f  // Anti-aliasing width (smaller = sharper edges)
    
    // Font loading is handled by the external NativeTextEngine
    // These methods are kept for API compatibility but delegate to the shared engine
    actual fun loadFont(fontBytes: ByteArray) {
        // No-op: Font loading should be done via the shared NativeTextEngine
    }
    
    actual fun loadFallbackFont(fontBytes: ByteArray) {
        // No-op: Font loading should be done via the shared NativeTextEngine
    }
    
    actual fun clearFallbackFonts() {
        // No-op: Font loading should be done via the shared NativeTextEngine
    }
    
    actual fun updateAtlas(uploads: List<GlyphUpload>) {
        if (uploads.isEmpty()) return
        
        for (upload in uploads) {
            if (upload.width <= 0 || upload.height <= 0) continue
            if (upload.x + upload.width > width || upload.y + upload.height > height) continue
            
            val data = upload.data
            
            // Create pixels for normal text atlas
            val normalPixels = IntArray(upload.width * upload.height)
            // Create pixels for shadow atlas
            val shadowPixels = IntArray(upload.width * upload.height)
            
            for (i in normalPixels.indices) {
                val offset = i * 4
                if (offset + 3 < data.size) {
                    val r = data[offset].toInt() and 0xFF
                    val g = data[offset + 1].toInt() and 0xFF
                    val b = data[offset + 2].toInt() and 0xFF
                    val sdfValue = (data[offset + 3].toInt() and 0xFF) / 255f
                    
                    // Normal text: Apply smoothstep with standard threshold (0.7)
                    val normalAlpha = smoothstep(
                        sdfThreshold - sdfSmoothing,
                        sdfThreshold + sdfSmoothing,
                        sdfValue
                    )
                    val normalA = (normalAlpha * 255f).toInt().coerceIn(0, 255)
                    normalPixels[i] = (normalA shl 24) or (r shl 16) or (g shl 8) or b
                    
                    // Shadow: Smoothstep falloff using full SDF range
                    val shadowOuterEdge = 0.4f
                    val shadowInnerEdge = sdfThreshold
                    val shadowAlpha = when {
                        sdfValue >= shadowInnerEdge -> 0f  // Inside text - covered by text layer
                        sdfValue <= shadowOuterEdge -> 0f  // At buffer edge
                        else -> {
                            // Smoothstep from outer edge to inner edge
                            val t = (sdfValue - shadowOuterEdge) / (shadowInnerEdge - shadowOuterEdge)
                            t * t * (3f - 2f * t)  // smoothstep
                        }
                    }
                    val shadowA = (shadowAlpha * 255f).toInt().coerceIn(0, 255)
                    shadowPixels[i] = (shadowA shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            
            // Set pixels to the normal atlas image
            atlasImage.setRGB(
                upload.x,
                upload.y,
                upload.width,
                upload.height,
                normalPixels,
                0,
                upload.width
            )
            
            // Set pixels to the shadow atlas image
            shadowAtlasImage.setRGB(
                upload.x,
                upload.y,
                upload.width,
                upload.height,
                shadowPixels,
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
    
    private fun ensureImageBitmaps() {
        if (isDirty || atlasImageBitmap == null || shadowAtlasImageBitmap == null) {
            atlasImageBitmap = atlasImage.toComposeImageBitmap()
            shadowAtlasImageBitmap = shadowAtlasImage.toComposeImageBitmap()
            isDirty = false
        }
    }
    
    actual fun DrawScope.drawGlyph(
        atlasRect: Rect,
        destOffset: Offset,
        destSize: Size,
        color: Color,
        shadow: Shadow?
    ) {
        if (!hasAnyData) return
        if (atlasRect.width <= 0 || atlasRect.height <= 0) return
        
        ensureImageBitmaps()
        val imageBitmap = atlasImageBitmap ?: return
        
        // Draw shadow first if specified
        if (shadow != null && shadow.blurRadius > 0f) {
            val shadowImageBitmap = shadowAtlasImageBitmap ?: return
            val shadowOffset = destOffset + shadow.offset
            
            // Apply blur radius as alpha threshold
            // Higher blurRadius -> show more of the distance gradient
            // blurRadius 0-20 maps to showing 0-100% of the gradient
            // We apply this by modulating the shadow color's alpha
            val blurIntensity = (shadow.blurRadius / 10f).coerceIn(0f, 1f)
            val modulatedColor = shadow.color.copy(alpha = shadow.color.alpha * blurIntensity)
            
            drawImage(
                image = shadowImageBitmap,
                srcOffset = IntOffset(atlasRect.left.toInt(), atlasRect.top.toInt()),
                srcSize = IntSize(atlasRect.width.toInt(), atlasRect.height.toInt()),
                dstOffset = IntOffset(shadowOffset.x.toInt(), shadowOffset.y.toInt()),
                dstSize = IntSize(destSize.width.toInt(), destSize.height.toInt()),
                colorFilter = ColorFilter.tint(modulatedColor, BlendMode.SrcIn)
            )
        }
        
        // Draw normal text on top
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
        shadowAtlasImageBitmap = null
        hasAnyData = false
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
