package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import java.io.File

@Composable
actual fun getPlatformContext(): Any? {
    return null  // JVM doesn't need context
}

/**
 * Get font bytes from FontFamily on JVM/Desktop.
 * Supports:
 * - Resource fonts from classpath
 * - File-based fonts
 * - System fonts as fallback
 */
actual fun getFontBytes(fontFamily: FontFamily?, platformContext: Any?): ByteArray? {
    // Try to extract font from FontFamily
    if (fontFamily is FontListFontFamily) {
        val fonts = fontFamily.fonts
        if (fonts.isNotEmpty()) {
            val font = fonts.first()
            
            // Try to get resource path via reflection
            try {
                val pathField = font.javaClass.getDeclaredField("resource")
                pathField.isAccessible = true
                val resourcePath = pathField.get(font) as? String
                if (resourcePath != null) {
                    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(resourcePath)
                        ?: font.javaClass.getResourceAsStream(resourcePath)
                    if (stream != null) {
                        return stream.use { it.readBytes() }
                    }
                }
            } catch (e: Exception) {
                // Not a resource font
            }
            
            // Try to get file path
            try {
                val fileField = font.javaClass.getDeclaredField("file")
                fileField.isAccessible = true
                val file = fileField.get(font) as? File
                if (file != null && file.exists()) {
                    return file.readBytes()
                }
            } catch (e: Exception) {
                // Not a file font
            }
        }
    }
    
    // Fallback to system fonts
    return getSystemFontBytes()
}

private fun getSystemFontBytes(): ByteArray? {
    val fontPaths = when {
        System.getProperty("os.name").lowercase().contains("win") -> listOf(
            "C:/Windows/Fonts/arial.ttf",
            "C:/Windows/Fonts/segoeui.ttf",
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/simsun.ttc"
        )
        System.getProperty("os.name").lowercase().contains("mac") -> listOf(
            "/System/Library/Fonts/Helvetica.ttc",
            "/System/Library/Fonts/SFNS.ttf",
            "/Library/Fonts/Arial.ttf"
        )
        else -> listOf(
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
            "/usr/share/fonts/noto/NotoSans-Regular.ttf"
        )
    }
    
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
