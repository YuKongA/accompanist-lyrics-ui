package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.runtime.Composable

@Composable
actual fun getPlatformContext(): Any? {
    return null  // JVM doesn't need context
}
