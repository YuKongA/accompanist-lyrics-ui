package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import java.io.File

@Composable
actual fun getPlatformContext(): Any? {
    return LocalContext.current
}

/**
 * Get font bytes from FontFamily on Android.
 * Supports:
 * - Resource fonts (e.g. FontFamily(Font(R.font.my_font)))
 * - System default fonts as fallback
 */
actual fun getFontBytes(fontFamily: FontFamily?, platformContext: Any?): ByteArray? {
    val context = platformContext as? Context ?: return getSystemFontBytes()
    
    // Try to extract font from FontFamily
    if (fontFamily is FontListFontFamily) {
        val fonts = fontFamily.fonts
        if (fonts.isNotEmpty()) {
            val font = fonts.first()
            // Check if it's a resource font
            try {
                // Use reflection to get the resource ID if it's a ResourceFont
                val resIdField = font.javaClass.getDeclaredField("resId")
                resIdField.isAccessible = true
                val resId = resIdField.getInt(font)
                if (resId != 0) {
                    return context.resources.openRawResource(resId).use { it.readBytes() }
                }
            } catch (e: Exception) {
                // Not a resource font or reflection failed
            }
            
            // Check if it's an asset font
            try {
                val pathField = font.javaClass.getDeclaredField("path")
                pathField.isAccessible = true
                val path = pathField.get(font) as? String
                if (path != null) {
                    return context.assets.open(path).use { it.readBytes() }
                }
            } catch (e: Exception) {
                // Not an asset font or reflection failed
            }
        }
    }
    
    // Fallback to system fonts
    return getSystemFontBytes()
}

/**
 * Get system fallback fonts for missing glyphs.
 * Returns fonts in priority order, prioritizing CJK and wide Unicode coverage.
 */
actual fun getSystemFallbackFontBytes(platformContext: Any?): List<ByteArray> {
    val result = mutableListOf<ByteArray>()
    
    // Fallback font paths in priority order
    // NotoSansCJK provides excellent CJK coverage
    // Other Noto fonts provide additional Unicode coverage
    val fallbackPaths = listOf(
        "/system/fonts/NotoSansCJK-Regular.ttc",
        "/system/fonts/NotoSansSC-Regular.otf",
        "/system/fonts/NotoSansTC-Regular.otf",
        "/system/fonts/NotoSansJP-Regular.otf",
        "/system/fonts/NotoSansKR-Regular.otf",
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/DroidSansFallback.ttf",
        "/system/fonts/Roboto-Regular.ttf"
    )
    
    for (path in fallbackPaths) {
        val file = File(path)
        if (file.exists() && file.canRead()) {
            try {
                result.add(file.readBytes())
            } catch (e: Exception) {
                // Skip unreadable fonts
            }
        }
    }
    
    return result
}

private fun getSystemFontBytes(): ByteArray? {
    val fontPaths = listOf(
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/NotoSansCJK-Regular.ttc",
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/DroidSans.ttf"
    )
    
    for (path in fontPaths) {
        val file = File(path)
        if (file.exists() && file.canRead()) {
            return try {
                file.readBytes()
            } catch (e: Exception) {
                null
            }
        }
    }
    
    return null
}
