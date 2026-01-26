package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.LocalResourceReader
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.resources.rememberResourceEnvironment

/**
 * Get platform-specific context for font loading.
 * Returns Context on Android, null on other platforms.
 */
@Composable
expect fun getPlatformContext(): Any?

/**
 * Get font bytes from a FontFamily using platform-specific mechanisms.
 * @param fontFamily The font family to resolve
 * @param platformContext Platform context (Context on Android, null elsewhere)
 * @return Font file bytes, or null if not available
 */
expect fun getFontBytes(fontFamily: FontFamily?, platformContext: Any?): ByteArray?

/**
 * Read font bytes from a Compose Multiplatform FontResource.
 * Uses the public LocalResourceReader API.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun rememberFontResourceBytes(fontResource: FontResource?): ByteArray? {
    val environment = getSystemResourceEnvironment()
    var fontBytes by remember(fontResource) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(fontResource, environment) {
        if (fontResource == null) {
            fontBytes = null
            return@LaunchedEffect
        }

        fontBytes = try {
            getFontResourceBytes(environment, fontResource)
        } catch (e: Exception) {
            null
        }
    }
    return fontBytes
}
