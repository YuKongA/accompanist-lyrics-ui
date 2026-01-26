package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.SystemFonts
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import java.io.File
import java.util.Locale

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
    val context = platformContext as? Context ?: return getSystemDefaultFontBytes()

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
    return getSystemDefaultFontBytes()
}

/**
 * Get system fallback font files for missing glyphs.
 * Returns fonts in priority order:
 * 1. System default font (vendor's default, e.g., MiSans, OppoSans, Roboto)
 * 2. Current locale font (if different from default)
 * 3. Fonts for other common locales (zh-CN, zh-TW, ja, ko, en)
 *
 * This uses dynamic discovery via SystemFonts API - no hardcoded font names.
 */
actual fun getSystemFallbackFontBytes(platformContext: Any?): List<ByteArray> {
    return getSystemFallbackFontFiles().mapNotNull { file ->
        try {
            file.readBytes()
        } catch (e: Exception) {
            android.util.Log.e("FontFallback", "Failed to read: ${file.absolutePath}", e)
            null
        }
    }
}

/**
 * Get system fallback font files by parsing Android font configuration XML.
 * This parses font_fallback.xml (modern) or fonts.xml (legacy) for reliable discovery.
 * Much more reliable than SystemFonts API, especially for OEM systems (MIUI, etc).
 */
fun getSystemFallbackFontFiles(): List<File> {
    val result = mutableListOf<File>()
    val addedPaths = mutableSetOf<String>()
    
    // Helper to add a font file if not already added
    fun addFont(path: String): Boolean {
        if (path in addedPaths) return false
        
        // Try multiple font directories
        val possiblePaths = listOf(
            "/system/fonts/$path",
            "/product/fonts/$path",
            "/system_ext/fonts/$path"
        )
        
        for (fullPath in possiblePaths) {
            val file = File(fullPath)
            if (file.exists() && file.canRead()) {
                addedPaths.add(path)
                result.add(file)
                android.util.Log.d("FontFallback", "Added fallback font: $fullPath")
                return true
            }
        }
        return false
    }
    
    // Target language tags to search for in font XML
    // Order matters: first found will be tried first
    val targetLangTags = listOf(
        null,           // Default sans-serif (no lang attribute) - first priority
        "und-Arab",     // Arabic script (covers Arabic, Persian, Urdu)
        "zh-Hans",      // Simplified Chinese
        "zh-Hant",      // Traditional Chinese
        "ja",           // Japanese
        "ko",           // Korean
        "und-Hebr",     // Hebrew script
        "und-Thai",     // Thai script
        "und-Deva",     // Devanagari (Hindi, Sanskrit, etc.)
        "ru"            // Russian (Cyrillic)
    )
    
    // Parse font XML files
    val fontFallbackXml = File("/system/etc/font_fallback.xml")
    val fontsXml = File("/system/etc/fonts.xml")
    
    // Try modern font_fallback.xml first, then legacy fonts.xml
    val xmlFile = if (fontFallbackXml.exists()) fontFallbackXml else fontsXml
    
    if (xmlFile.exists()) {
        android.util.Log.d("FontFallback", "Parsing font config: ${xmlFile.absolutePath}")
        
        for (langTag in targetLangTags) {
            val fonts = parseFontXmlForLang(xmlFile, langTag)
            for (fontPath in fonts) {
                addFont(fontPath)
            }
        }
    } else {
        android.util.Log.w("FontFallback", "No font XML found, falling back to SystemFonts API")
        // Fallback to SystemFonts API if no XML available
        val availableFonts = SystemFonts.getAvailableFonts()
        for (font in availableFonts.take(10)) {
            font.file?.let { file ->
                if (file.absolutePath !in addedPaths) {
                    addedPaths.add(file.absolutePath)
                    result.add(file)
                }
            }
        }
    }
    
    android.util.Log.d("FontFallback", "Total fallback fonts: ${result.size}")
    return result
}

/**
 * Parse font XML file and extract font paths for a specific language tag.
 * @param xmlFile The font configuration XML file
 * @param targetLang The language tag to match (e.g., "und-Arab", "zh-Hans"), or null for default sans-serif
 */
private fun parseFontXmlForLang(xmlFile: File, targetLang: String?): List<String> {
    val paths = mutableListOf<String>()
    
    try {
        val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(xmlFile.inputStream(), "UTF-8")
        
        var inTargetFamily = false
        var inFontElement = false
        var currentFontText = StringBuilder()
        var eventType = parser.eventType
        
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "family" -> {
                            val familyName = parser.getAttributeValue(null, "name")
                            val familyLang = parser.getAttributeValue(null, "lang")
                            
                            inTargetFamily = if (targetLang == null) {
                                // Looking for default sans-serif (no lang, name="sans-serif")
                                familyLang == null && familyName == "sans-serif"
                            } else {
                                // Looking for specific language
                                familyLang?.contains(targetLang) == true
                            }
                        }
                        "font" -> {
                            if (inTargetFamily) {
                                inFontElement = true
                                currentFontText = StringBuilder()
                            }
                        }
                        // Skip other tags like <axis>, they're self-closing or nested
                    }
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> {
                    if (inFontElement) {
                        currentFontText.append(parser.text)
                    }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "font" -> {
                            if (inFontElement) {
                                val fileName = currentFontText.toString().trim()
                                if (fileName.isNotEmpty() && fileName.endsWith(".ttf", ignoreCase = true) 
                                    || fileName.endsWith(".otf", ignoreCase = true) 
                                    || fileName.endsWith(".ttc", ignoreCase = true)) {
                                    paths.add(fileName)
                                }
                                inFontElement = false
                            }
                        }
                        "family" -> {
                            inTargetFamily = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    } catch (e: Exception) {
        android.util.Log.e("FontFallback", "Error parsing ${xmlFile.name}: ${e.message}")
    }
    
    if (paths.isNotEmpty()) {
        android.util.Log.d("FontFallback", "Found ${paths.size} fonts for lang=$targetLang: ${paths.take(3)}...")
    }
    
    return paths
}

/**
 * Get system default font bytes.
 * Uses dynamic discovery to find the system's default font.
 */
private fun getSystemDefaultFontBytes(): ByteArray? {
    val availableFonts = SystemFonts.getAvailableFonts()

    // Find the best match for system default (weight 400, non-italic, generic)
    val defaultFont = availableFonts.filter { font ->
        font.style.weight == 400 && font.style.slant == 0
    }.minByOrNull { font ->
        // Prefer fonts without specific locale restrictions
        val locales = font.localeList
        if (locales.isEmpty) 0 else locales.size()
    }

    return try {
        defaultFont?.file?.readBytes()
    } catch (e: Exception) {
        android.util.Log.e("FontFallback", "Failed to read default font", e)
        null
    }
}
