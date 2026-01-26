package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

@Composable
actual fun getPlatformContext(): Any? {
    return null  // Apple platforms don't need context
}
/**
 * Get font bytes from FontFamily on Apple platforms.
 * TODO: Implement using CoreText APIs via cinterop.
 */
actual fun getFontBytes(fontFamily: FontFamily?, platformContext: Any?): ByteArray? {
    // TODO: Use CoreText to get font data
    // CTFontCopyTable can be used to get font data
    return null
}

/**
 * Get system fallback fonts on Apple platforms.
 * TODO: Implement using CoreText APIs via cinterop.
 */
actual fun getSystemFallbackFontBytes(platformContext: Any?): List<ByteArray> {
    // TODO: Use CoreText to enumerate system fonts
    // CTFontCreateUIFontForLanguage can be used to get localized system fonts
    return emptyList()
}
