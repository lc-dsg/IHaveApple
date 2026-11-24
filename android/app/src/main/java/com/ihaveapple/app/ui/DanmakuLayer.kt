package com.ihaveapple.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihaveapple.app.data.DanmakuItem
import kotlin.math.abs
import kotlin.math.roundToInt

private const val FLY_SECONDS = 7.5
private const val LANES = 7

/**
 * 弹幕层：按播放时钟直接算横向位置（不维护逐条动画状态），
 * 拖动进度条后位置立刻对齐。文字从右往左穿过后消失。
 */
@Composable
fun DanmakuLayer(items: List<DanmakuItem>, positionSec: Double, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat()
        val topPx = with(density) { 64.dp.toPx() }
        val laneHeight = with(density) { 30.dp.toPx() }
        val fontSize = 16.sp
        val visible = items.asSequence()
            .mapNotNull { d ->
                val elapsed = positionSec - d.offsetMs / 1000.0
                if (elapsed < 0 || elapsed > FLY_SECONDS) return@mapNotNull null
                val lane = (abs(d.text.hashCode()) % LANES)
                val estWidth = d.text.length * 17f * density.density
                val x = width - (elapsed / FLY_SECONDS) * (width + estWidth)
                d to (x to (topPx + lane * laneHeight))
            }
            .take(24)
            .toList()
        for ((d, pos) in visible) {
            Text(
                text = d.text,
                color = Color(0xE6F3EDE6),
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                style = TextStyle(
                    shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), 4f),
                ),
                modifier = Modifier
                    .offset {
                        IntOffset(pos.first.roundToInt(), pos.second.roundToInt())
                    },
            )
        }
    }
}