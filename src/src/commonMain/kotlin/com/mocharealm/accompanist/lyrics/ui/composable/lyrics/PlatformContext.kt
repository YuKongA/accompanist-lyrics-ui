package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.runtime.Composable

/**
 * Get platform-specific context for font loading.
 * Returns Context on Android, null on other platforms.
 */
@Composable
expect fun getPlatformContext(): Any?
