package com.mocharealm.accompanist.lyrics.ui.composable.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.utils.isRtl
import com.mocharealm.gaze.capsule.ContinuousRoundedRectangle

@Composable
fun SyncedLineItem(
    line: SyncedLine,
    isFocused: Boolean,
    onLineClicked: (ISyncedLine) -> Unit,
    onLinePressed: (ISyncedLine) -> Unit,
    textStyle: TextStyle,
    textColor: Color,
    blendMode: BlendMode
) {
    // 使用 remember 缓存计算结果，避免重组时重新计算
    val isLineRtl = remember(line.content) { line.content.isRtl() }

    // 动画数值
    val scaleState = animateFloatAsState(
        targetValue = if (isFocused) 1f else 0.98f,
        animationSpec = tween(if (isFocused) 600 else 300),
        label = "scale"
    )
    val alphaState = animateFloatAsState(
        targetValue = if (isFocused) 1f else 0.4f,
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 优化点：使用 Modifier.graphicsLayer 承载动画，避免触发布局测量(Measurement)
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
                alpha = alphaState.value
                transformOrigin = TransformOrigin(if (isLineRtl) 1f else 0f, 1f)
                this.blendMode = blendMode
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .clip(ContinuousRoundedRectangle(8.dp))
            .combinedClickable(
                onClick = { onLineClicked(line) },
                onLongClick = { onLinePressed(line) }
            )
    ) {
        Column(
            Modifier
                .align(if (isLineRtl) Alignment.TopEnd else Alignment.TopStart)
                .padding(vertical = 8.dp, horizontal = 16.dp)
        ) {
            Text(
                text = line.content,
                style = textStyle,
                color = textColor
            )
            line.translation?.let {
                Text(
                    text = it,
                    color = textColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}