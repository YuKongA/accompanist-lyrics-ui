package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Font
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.FontStyle as SkiaFontStyle

// Cache for Skia Typefaces on JVM
private val typefaceCache = mutableMapOf<String, Typeface>()

actual fun getGlyphPath(
    text: String,
    style: TextStyle,
    density: Density,
    fontFamilyResolver: FontFamily.Resolver,
    context: Any?  // Not used on JVM
): Path {
    val fontSize = with(density) { style.fontSize.toPx() }
    
    val fontWeight = style.fontWeight ?: FontWeight.Normal
    val fontStyle = style.fontStyle ?: FontStyle.Normal
    val cacheKey = "${style.fontFamily}_${fontWeight.weight}_${fontStyle.value}"
    
    // Use cache or resolve typeface
    val typeface = typefaceCache.getOrPut(cacheKey) {
        val typefaceState = fontFamilyResolver.resolve(
            fontFamily = style.fontFamily,
            fontWeight = fontWeight,
            fontStyle = fontStyle
        )
        typefaceState.value as org.jetbrains.skia.Typeface
    }
    
    val font = Font(typeface, fontSize)
    
    // Get glyphs for the text
    val glyphs = font.getStringGlyphs(text)
    val widths = font.getWidths(glyphs)
    val skiaPath = org.jetbrains.skia.Path()
    
    var x = 0f
    for (i in glyphs.indices) {
        val path = font.getPath(glyphs[i])
        if (path != null) {
            skiaPath.addPath(path, x, 0f)
        }
        x += widths[i]
    }
    
    return skiaPath.asComposePath()
}
