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

/**
 * Get system fallback fonts for missing glyphs on JVM/Desktop.
 * Returns fonts in priority order, prioritizing CJK and wide Unicode coverage.
 */
actual fun getSystemFallbackFontBytes(platformContext: Any?): List<ByteArray> {
    val result = mutableListOf<ByteArray>()
    
    val osName = System.getProperty("os.name").lowercase()
    println("getSystemFallbackFontBytes: OS=$osName")
    
    val fallbackPaths = when {
        osName.contains("win") -> listOf(
            "C:/Windows/Fonts/msyh.ttc",      // Microsoft YaHei - Chinese
            "C:/Windows/Fonts/simsun.ttc",    // SimSun - Chinese
            "C:/Windows/Fonts/meiryo.ttc",    // Meiryo - Japanese
            "C:/Windows/Fonts/malgun.ttf",    // Malgun Gothic - Korean
            "C:/Windows/Fonts/arial.ttf",     // Arial - Latin
            "C:/Windows/Fonts/seguisym.ttf"   // Segoe UI Symbol - Emoji/symbols
        )
        osName.contains("mac") -> listOf(
            "/System/Library/Fonts/PingFang.ttc",         // Chinese
            "/System/Library/Fonts/Hiragino Sans GB.ttc", // Chinese
            "/System/Library/Fonts/Hiragino.ttc",         // Japanese
            "/System/Library/Fonts/AppleGothic.ttf",      // Korean
            "/System/Library/Fonts/Helvetica.ttc",        // Latin
            "/System/Library/Fonts/Apple Color Emoji.ttc" // Emoji
        )
        else -> listOf(
            "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"
        )
    }
    
    for (path in fallbackPaths) {
        val file = File(path)
        val exists = file.exists()
        val canRead = file.canRead()
        println("  Font path: $path, exists=$exists, canRead=$canRead")
        if (exists && canRead) {
            try {
                val bytes = file.readBytes()
                result.add(bytes)
                println("  -> Loaded ${bytes.size} bytes")
            } catch (e: Exception) {
                println("  -> Failed to read: ${e.message}")
            }
        }
    }
    
    println("getSystemFallbackFontBytes: Loaded ${result.size} fallback fonts")
    return result
}
