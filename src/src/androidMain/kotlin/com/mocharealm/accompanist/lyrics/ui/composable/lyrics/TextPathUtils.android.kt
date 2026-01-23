package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import android.content.Context
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Data
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontVariation
import org.jetbrains.skia.PathVerb
import org.jetbrains.skia.Typeface
import kotlin.math.abs

// Cache for Skia Typefaces to avoid recreating from bytes
private val typefaceCache = mutableMapOf<String, Typeface>()

/**
 * Extract font bytes from FontFamily.
 * Returns null if FontFamily is not a resource-based font.
 */
private fun extractFontBytes(
    context: Context,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    fontStyle: FontStyle
): ByteArray? {
    if (fontFamily !is FontListFontFamily) return null

    // Find matching font from the font list
    val font = fontFamily.fonts.find {
        it.weight == fontWeight && it.style == fontStyle
    } ?: fontFamily.fonts.firstOrNull()

    if (font == null) return null

    // Use reflection to get the font path from compose resources
    try {
        // Font objects have a 'path' field when created from resources
        val assetManager = context.assets

        // Try to access internal fields to get the asset path
        val fontClass = font::class.java
        val fields = fontClass.declaredFields

        for (field in fields) {
            field.isAccessible = true
            val value = field.get(font) as? String

            // Look for string fields that might contain the path
            if (value is String && (value.endsWith(".ttf") || value.endsWith(".otf"))) {
                println("📁 Found raw font path: $value")

                // Clean up the path: remove "file:", "asset:" prefixes
                // Compose resources often start with "file:///android_asset/" or just "composeResources/"
                var cleanPath = value!!

                if (cleanPath.startsWith("file:///android_asset/")) {
                    cleanPath = cleanPath.substring("file:///android_asset/".length)
                } else if (cleanPath.startsWith("file:")) {
                    cleanPath = cleanPath.substring("file:".length)
                } else if (cleanPath.startsWith("asset:")) {
                    cleanPath = cleanPath.substring("asset:".length)
                }

                // Remove leading slashes if any
                while (cleanPath.startsWith("/") || cleanPath.startsWith("\\")) {
                    cleanPath = cleanPath.substring(1)
                }

                println("📂 Cleaned font path: $cleanPath")
                return assetManager.open(cleanPath).readBytes()
            }
        }

        println("⚠️ Could not extract font path from Font object")
        return null
    } catch (e: Exception) {
        println("⚠️ Failed to extract font bytes: ${e.message}")
        e.printStackTrace()
        return null
    }
}

actual fun getGlyphPath(
    text: String,
    style: TextStyle,
    density: Density,
    fontFamilyResolver: FontFamily.Resolver,
    context: Any?
): Path {
    val fontSize = with(density) { style.fontSize.toPx() }
    val androidContext = context as? Context

    // 1. Try to use custom font via Skia first (to avoid Android API bugs)
    if (androidContext != null) {
        val fontWeight = style.fontWeight ?: FontWeight.Normal
        val fontStyle = style.fontStyle ?: FontStyle.Normal
        val cacheKey = "${style.fontFamily}_${fontWeight.weight}_${fontStyle.value}"

        val skiaTypeface = typefaceCache.getOrPut(cacheKey) {
            val fontBytes =
                extractFontBytes(androidContext, style.fontFamily, fontWeight, fontStyle)
            if (fontBytes != null) {
                val data = Data.makeFromBytes(fontBytes)
                findBestTypeface(data, fontWeight, fontStyle)
            } else {
                Typeface.makeEmpty()
            }
        }

        val font = Font(skiaTypeface, fontSize)
        val glyphs = font.getStringGlyphs(text)

        // Allow if no glyphs are missing (ID 0 is usually .notdef/missing glyph)
        // Note: some fonts might map space to 0, or have valid glyphs at 0,
        // but standard behavior is 0 = missing.
        // We'll assume if any glyph is 0, we might want fallback,
        // EXCEPT if the text itself is empty or whitespace only which might be fine?
        // Actually, let's just check for 0.
        val hasMissingGlyphs = glyphs.any { it.toInt() == 0 }

        if (!hasMissingGlyphs) {
            return createPathFromTypeface(text, skiaTypeface, fontSize, style)
        } else {
            println("⚠️ Custom font missing glyphs for '$text', falling back to system font.")
        }
    }

    // 2. Fallback: Use Android Native Paint (correctly handles system fallback)
    return createPathUsingAndroidPaint(text, style, fontSize, fontFamilyResolver)
}

private fun createPathUsingAndroidPaint(
    text: String,
    style: TextStyle,
    fontSize: Float,
    fontFamilyResolver: FontFamily.Resolver
): Path {
    val paint = android.graphics.Paint().apply {
        textSize = fontSize // Set size first
        isAntiAlias = true

        // Resolve typeface using Compose's resolver to get the correct system font stack
        val typefaceState = fontFamilyResolver.resolve(
            fontFamily = style.fontFamily,
            fontWeight = style.fontWeight ?: FontWeight.Normal,
            fontStyle = style.fontStyle ?: FontStyle.Normal
        )
        typeface =
            typefaceState.value as? android.graphics.Typeface ?: android.graphics.Typeface.DEFAULT
    }

    val androidPath = android.graphics.Path()

    // Calculate vertical offset to match Skia's coordinate system (baseline at 0)
    // Compose/Skia paths usually put baseline at y=0.
    // paint.getTextPath puts baseline at (x, y). 
    // Wait, let's look at the original Skia implementation:
    // val verticalOffset = -metrics.ascent
    // skiaPath.addPath(glyphPath, x, verticalOffset)
    // accessing 'metrics' from skia font. 

    // In Android Paint:
    // getTextPath(text, start, end, x, y, path)
    // The y coordinate corresponds to the baseline.

    // However, the previous Skia implementation did:
    // verticalOffset = -metrics.ascent
    // addPath(..., y = verticalOffset)
    // This shifts the text DOWN so that the TOP of the text (ascent) is at 0?
    // No, -ascent is positive. 0 is the top of the canvas? 
    // Skia Font metrics: ascent is negative (above baseline).
    // So -metrics.ascent is positive distance from top to baseline.
    // If we draw at y = -ascent, the baseline is at -ascent. Top is at 0.
    // So the coordinate system is: (0,0) is the top-left of the bounding box of the line (roughly).

    // To match this with Android Paint:
    // paint.fontMetrics.ascent is also negative.
    // So passing y = -paint.fontMetrics.ascent should place the baseline at the same relative position.

    val verticalOffset = -paint.fontMetrics.ascent
    paint.getTextPath(text, 0, text.length, 0f, verticalOffset, androidPath)

    return androidPath.asComposePath()
}

/**
 * 寻找最匹配的字体：可变字体优先，否则遍历 TTC 索引
 */
private fun findBestTypeface(
    data: Data,
    targetWeight: FontWeight,
    targetStyle: FontStyle
): Typeface {
    val fontMgr = FontMgr.default

    // 1. 尝试作为可变字体处理 (通常在 index 0)
    val baseTypeface = fontMgr.makeFromData(data, 0) ?: return Typeface.makeEmpty()

    // 检查是否为可变字体
    val axes = baseTypeface.variationAxes
    if (axes != null && axes.any { it.tag == "wght" }) {
        // 使用 FontVariation 结构体来设置数值
        // tag 是 String (4个字符)，value 是 Float
        val variations = arrayOf(
            FontVariation("wght", targetWeight.weight.toFloat())
        )

        val matchedTf = baseTypeface.makeClone(variations, 0)

        println("🎨 Variable Font Applied: wght = ${targetWeight.weight}")
        return matchedTf
    }

    // 2. 如果不是可变字体，遍历 TTC 索引寻找最接近的字重
    var bestMatch = baseTypeface
    var minDiff = abs(baseTypeface.fontStyle.weight - targetWeight.weight)

    // 假设 TTC 内部最多 15 个字体
    for (i in 1 until 15) {
        val tf = fontMgr.makeFromData(data, i) ?: break
        val diff = abs(tf.fontStyle.weight - targetWeight.weight)

        // 同时考虑倾斜度匹配（如果是 Italic）
        val slantMatches =
            (targetStyle == FontStyle.Italic) == (tf.fontStyle.slant != FontSlant.UPRIGHT)

        if (diff < minDiff && slantMatches) {
            minDiff = diff
            bestMatch = tf
        }

        if (minDiff == 0 && slantMatches) break
    }

    println("🎯 Selected TTC Index with weight: ${bestMatch.fontStyle.weight}")
    return bestMatch
}

private fun createPathFromTypeface(
    text: String,
    typeface: Typeface,
    fontSize: Float,
    style: TextStyle
): Path {
    // 微调 Font 参数
    val font = Font(typeface, fontSize).apply {
        // 如果字体库本身没提供足够粗的字重，且目标是 Bold，开启模拟加粗
        val targetWeight = style.fontWeight ?: FontWeight.Normal
        if (targetWeight >= FontWeight.SemiBold && typeface.fontStyle.weight < 600) {
            isEmboldened = true
        }
        isLinearMetrics = true
    }

    val metrics = font.metrics
    val glyphs = font.getStringGlyphs(text)
    val widths = font.getWidths(glyphs)
    val skiaPath = org.jetbrains.skia.Path()

    // 解决位置不对的关键：补偿 Ascent
    // Skia 的 y=0 是基线，ascent 是负值（基线以上高度）
    // 加上 -ascent 使得文字顶部对齐 y=0
    val verticalOffset = -metrics.ascent

    var x = 0f
    for (i in glyphs.indices) {
        val glyphPath = font.getPath(glyphs[i])
        if (glyphPath != null) {
            skiaPath.addPath(glyphPath, x, verticalOffset)
        }
        x += widths[i]
    }

    return skiaPath.asComposePath()
}

/**
 * Helper function to create path from typeface and text.
 */
private fun createPathFromTypeface(text: String, typeface: Typeface, fontSize: Float): Path {
    val font = Font(typeface, fontSize)
    val metrics = font.metrics

    val glyphs = font.getStringGlyphs(text)
    val widths = font.getWidths(glyphs)
    val skiaPath = org.jetbrains.skia.Path()

    // 计算垂直偏移：将 Baseline 移动到垂直居中或顶端
    // metrics.ascent 是负值（基线以上），这里取绝对值
    val verticalOffset = -metrics.ascent

    var x = 0f
    for (i in glyphs.indices) {
        val glyphPath = font.getPath(glyphs[i])
        if (glyphPath != null) {
            // 在这里加上 verticalOffset，确保文字不会“飞”到 Canvas 外面
            skiaPath.addPath(glyphPath, x, verticalOffset)
        }
        x += widths[i]
    }

    return skiaPath.asComposePath()
}

fun org.jetbrains.skia.Path.asComposePath(): Path {
    val androidPath = android.graphics.Path()
    val iter = this.iterator()

    while (iter.hasNext()) {
        val segment = iter.next()
        when (segment?.verb) {
            PathVerb.MOVE -> {
                // p0 始终存在于 MOVE 操作中
                segment.p0?.let { androidPath.moveTo(it.x, it.y) }
            }

            PathVerb.LINE -> {
                // LINE 使用 p1 作为终点
                segment.p1?.let { androidPath.lineTo(it.x, it.y) }
            }

            PathVerb.QUAD -> {
                // QUAD 使用 p1(控制点) 和 p2(终点)
                val p1 = segment.p1
                val p2 = segment.p2
                if (p1 != null && p2 != null) {
                    androidPath.quadTo(p1.x, p1.y, p2.x, p2.y)
                }
            }

            PathVerb.CONIC -> {
                // Android 原生 Path 不直接支持 Conic，通常转换为 Quad
                // 或者使用 Skia 内部逻辑将其近似
                val p1 = segment.p1
                val p2 = segment.p2
                if (p1 != null && p2 != null) {
                    androidPath.quadTo(p1.x, p1.y, p2.x, p2.y)
                }
            }

            PathVerb.CUBIC -> {
                // CUBIC 使用 p1, p2(控制点) 和 p3(终点)
                val p1 = segment.p1
                val p2 = segment.p2
                val p3 = segment.p3
                if (p1 != null && p2 != null && p3 != null) {
                    androidPath.cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
                }
            }

            PathVerb.CLOSE -> {
                androidPath.close()
            }

            PathVerb.DONE -> break
            else -> continue
        }
    }

    return androidPath.asComposePath()
}

