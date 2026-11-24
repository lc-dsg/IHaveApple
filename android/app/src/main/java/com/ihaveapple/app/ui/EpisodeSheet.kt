package com.ihaveapple.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihaveapple.app.player.UiState
import kotlin.math.ceil
import kotlin.math.floor

private const val PAGE_SIZE = 30
private const val COLUMNS = 6

@Composable
fun EpisodeSheet(
    state: UiState,
    positionSec: Double,
    durationSec: Double,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodes = state.episodes
    val currentNumber = state.episode?.number ?: 1
    val pages = if (episodes.isEmpty()) 0 else ceil(episodes.size / PAGE_SIZE.toFloat()).toInt()
    var page by rememberSaveable(state.drama?.id) { mutableIntStateOf((currentNumber - 1).coerceAtLeast(0) / PAGE_SIZE) }

    LaunchedEffect(currentNumber) {
        page = ((currentNumber - 1).coerceAtLeast(0)) / PAGE_SIZE
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.18f to ScrimDeep,
                    1f to ScrimDeep,
                )
            )
            .padding(top = 48.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SpaceMd, vertical = SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("选集", color = Subtitle, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Spacer(Modifier.width(SpaceSm))
            Text("共 ${state.episodeCount} 集", color = Aside, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            IconBtn(Icons.Filled.Close, "关闭", size = 36.dp, onClick = onClose)
        }

        if (pages > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpaceMd),
                horizontalArrangement = Arrangement.spacedBy(SpaceXs),
            ) {
                items((0 until pages).toList()) { p ->
                    val first = p * PAGE_SIZE + 1
                    val last = minOf(episodes.size, (p + 1) * PAGE_SIZE)
                    val active = p == page
                    Box(
                        Modifier
                            .height(28.dp)
                            .clip(PillShape)
                            .background(if (active) Subtitle14 else Color.Transparent)
                            .clickable { page = p }
                            .padding(horizontal = SpaceMd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$first–$last",
                            color = if (active) Subtitle else Aside,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(SpaceSm))
        }

        val start = page * PAGE_SIZE
        val pageEpisodes = episodes.drop(start).take(PAGE_SIZE)
        val rows = ceil(pageEpisodes.size / COLUMNS.toFloat()).toInt()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SpaceMd)
                .padding(bottom = SpaceSm),
            verticalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
                    for (r in 0 until rows) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpaceXs),
                ) {
                    for (c in 0 until COLUMNS) {
                        val ep = pageEpisodes.getOrNull(r * COLUMNS + c)
                        if (ep == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val isCurrent = ep.number == currentNumber
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(RadiusMd))
                                    .background(if (isCurrent) Crabapple else Subtitle08)
                                    .clickable(enabled = ep.playable) { onPick(ep.number) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    ep.number.toString(),
                                    style = EpisodeNumberStyle,
                                    color = if (isCurrent) Subtitle else if (ep.playable) Subtitle.copy(alpha = 0.86f) else Aside.copy(alpha = 0.4f),
                                    textAlign = TextAlign.Center,
                                )
                                if (isCurrent && durationSec > 0) {
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .background(CrabappleDeep)
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth((positionSec / durationSec).coerceIn(0.0, 1.0).toFloat())
                                                .height(2.dp)
                                                .background(Subtitle.copy(alpha = 0.85f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}