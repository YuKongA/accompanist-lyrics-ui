package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density

expect fun getGlyphPath(
    text: String,
    style: TextStyle,
    density: Density,
    fontFamilyResolver: FontFamily.Resolver,
    context: Any? = null  // Platform-specific context (Android Context on Android, null on others)
): Path

