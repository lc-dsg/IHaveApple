package com.ihaveapple.app.ui

import android.app.Activity
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ihaveapple.app.data.Prefs
import com.ihaveapple.app.player.LibraryTab
import com.ihaveapple.app.player.PlayerMode
import com.ihaveapple.app.player.PlayerViewModel
import com.ihaveapple.app.player.UiState
import com.ihaveapple.app.util.formatClock
import kotlinx.coroutines.launch
import kotlin.math.abs

private val CHANNELS = listOf("真人剧", "漫剧", "推荐")

@Composable
fun PlayerScreen(vm: PlayerViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val timePos by vm.mpv.timePos.collectAsStateWithLifecycle()
    val duration by vm.mpv.duration.collectAsStateWithLifecycle()
    val paused by vm.mpv.paused.collectAsStateWithLifecycle()
    val buffering by vm.mpv.buffering.collectAsStateWithLifecycle()
    val loaded by vm.mpv.loaded.collectAsStateWithLifecycle()
    val danmaku by vm.danmaku.collectAsStateWithLifecycle()

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var speedMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }

    val playing = loaded && !paused
    val showBottom = state.controlsVisible || !playing || state.sheetOpen
    val overlayOpen = state.libraryTab != null || state.settingsOpen

    KeepScreenOn(prefs, playing)
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(Stage)) {
        // ---- 画卷邻居：拖动时从上下滑入的下一部 / 下一集封面 ----
        if (dragOffset != 0f) {
            val dir = if (dragOffset < 0) 1 else -1
            val neighbor = if (state.mode == PlayerMode.Series) {
                state.episodes.getOrNull(state.episodeIndex + dir)?.coverUrl
            } else {
                val list = state.dramas
                var idx = state.feedIndex + dir
                if (idx < 0) idx = list.lastIndex
                if (idx > list.lastIndex) idx = 0
                list.getOrNull(idx)?.coverUrl
            }?.takeIf { it.isNotBlank() } ?: state.drama?.coverUrl
            if (!neighbor.isNullOrBlank() && vm.canStep(dir)) {
                val screenH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
                Cover(
                    neighbor,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = dragOffset + if (dir > 0) screenH else -screenH
                        },
                    corner = 0.dp,
                )
            }
        }

        // ---- 画面 ----
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = dragOffset }
        ) {
            if (!loaded || state.loading) {
                Cover(
                    state.drama?.coverUrl.orEmpty(),
                    Modifier.fillMaxSize(),
                    corner = 0.dp,
                )
            }
            VideoSurface(vm)
            if (prefs.danmaku && loaded) {
                DanmakuLayer(
                    items = danmaku,
                    positionSec = timePos,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // ---- 手势：点按暂停，上下拖换节目 / 换集 ----
        if (!overlayOpen) {
            GestureLayer(
                vm = vm,
                state = state,
                setOffset = { dragOffset = it },
            )
        }

        // ---- 加载 / 错误 / 缓冲 ----
        val effectiveDuration = when {
            duration > 0 -> duration
            else -> state.estimateDuration
        }

        if (state.loading && state.error == null) {
            CircularProgressIndicator(
                color = Subtitle.copy(alpha = 0.85f),
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
            )
        }
        if (!state.loading && state.error == null && buffering) {
            CircularProgressIndicator(
                color = Subtitle.copy(alpha = 0.6f),
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(26.dp),
            )
        }
        state.error?.let { msg ->
            ErrorPanel(msg, vm::retry, Modifier.align(Alignment.Center))
        }

        if (!state.loading && state.error == null && state.drama == null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = SpaceXl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (state.offline) "网络未连接" else "「${state.channel}」暂时没有内容",
                    color = Subtitle,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(SpaceLg))
                Box(
                    Modifier
                        .clip(PillShape)
                        .background(Crabapple)
                        .clickable(onClick = vm::retry)
                        .padding(horizontal = SpaceXl, vertical = SpaceSm),
                ) {
                    Text("重试", color = Subtitle, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // 暂停时画面中央的播放键
        if (!state.loading && state.error == null && !state.ended && paused && loaded) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Stage60)
                    .clickable { vm.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    Icons.Filled.PlayArrow,
                    "继续播放",
                    tint = Subtitle,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        // ---- 顶部 ----
        AnimatedVisibility(
            visible = showBottom && !state.ended,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopChrome(
                state = state,
                vm = vm,
                topInset = topInset,
            )
        }

        // ---- 集号卡片 ----
        AnimatedVisibility(
            visible = state.titleCard != null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(240)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = SpaceLg, top = topInset + 44.dp),
        ) {
            Text(
                state.titleCard.orEmpty(),
                color = Subtitle,
                fontFamily = DisplayFont,
                fontSize = 56.sp,
                lineHeight = 56.sp,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        Color.Black.copy(alpha = 0.6f),
                        Offset(0f, 2f),
                        6f,
                    )
                ),
            )
        }

        // ---- 底部 ----
        AnimatedVisibility(
            visible = showBottom && !state.ended,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    visible = state.sheetOpen,
                    enter = fadeIn(tween(160)) + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut(tween(160)) + slideOutVertically(targetOffsetY = { it }),
                ) {
                    EpisodeSheet(
                        state = state,
                        positionSec = timePos,
                        durationSec = effectiveDuration,
                        onPick = vm::selectEpisode,
                        onClose = vm::closeSheet,
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (state.sheetOpen) {
                                Modifier.background(ScrimDeep)
                            } else {
                                Modifier.background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        0.35f to Stage60.copy(alpha = 0.5f),
                                        1f to Stage.copy(alpha = 0.8f),
                                    )
                                )
                            }
                        )
                        .padding(horizontal = SpaceMd)
                        .padding(bottom = bottomInset),
                ) {
                    if (state.mode == PlayerMode.Feed && !state.sheetOpen) {
                        DramaInfo(
                            state = state,
                            onShowAll = {
                                vm.enterSeriesMode()
                                vm.toast("上下滑动切换集数")
                            },
                        )
                    }
                    Spacer(Modifier.height(SpaceXs))
                    SeekBar(
                        position = timePos.coerceAtLeast(0.0),
                        duration = effectiveDuration,
                        onSeek = { vm.seekTo(it) },
                    )
                    ControlRow(
                        state = state,
                        prefs = prefs,
                        paused = paused,
                        timePos = timePos,
                        duration = effectiveDuration,
                        speedMenu = speedMenu,
                        qualityMenu = qualityMenu,
                        onSpeedMenu = { speedMenu = it },
                        onQualityMenu = { qualityMenu = it },
                        vm = vm,
                    )
                }
            }
        }

        // ---- 全剧终 ----
        if (state.ended) {
            EndPanel(
                state = state,
                onReplay = vm::replay,
                onExit = {
                    if (state.mode == PlayerMode.Series) vm.exitSeriesMode()
                },
            )
        }

        // ---- 覆盖层 ----
        LibraryOverlay(state, vm)
        SettingsOverlay(state, prefs, vm)

        // ---- Toast ----
        AnimatedVisibility(
            visible = state.toast != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 130.dp),
        ) {
            Box(
                Modifier
                    .clip(PillShape)
                    .background(Scrim)
                    .padding(horizontal = SpaceXl, vertical = 10.dp),
            ) {
                Text(state.toast.orEmpty(), color = Subtitle, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun KeepScreenOn(prefs: Prefs, playing: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(prefs.keepAwake, playing) {
        if (prefs.keepAwake && playing) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
private fun VideoSurface(vm: PlayerViewModel) {
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        vm.mpv.attachSurface(Surface(st))
                    }

                    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                        vm.mpv.detachSurface()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) = Unit
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun GestureLayer(
    vm: PlayerViewModel,
    state: UiState,
    setOffset: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val threshold = with(density) { 60.dp.toPx() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val stateKey = state.mode.name + "/" + state.drama?.id + "/" + state.episodeIndex + "/" + state.dramas.size

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(stateKey) {
                detectTapGestures(onTap = { vm.togglePlay() })
            }
            .pointerInput(stateKey) {
                var total = 0f
                var tracker: VelocityTracker? = null
                detectVerticalDragGestures(
                    onDragStart = {
                        total = 0f
                        tracker = VelocityTracker().apply {
                            addPosition(System.currentTimeMillis(), Offset(0f, 0f))
                        }
                        vm.showControls()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        tracker?.addPosition(change.uptimeMillis, change.position)
                        val dir = if (dragAmount < 0) 1 else -1
                        val factor = if (vm.canStep(dir)) 1f else 0.25f
                        total += dragAmount * factor
                        setOffset(total)
                    },
                    onDragEnd = {
                        val velocity = tracker?.calculateVelocity()?.y ?: 0f
                        tracker = null
                        val dir = if (total < 0) 1 else -1
                        val commit = abs(total) > threshold || abs(velocity) > 500f
                        val h = size.height.toFloat()
                        if (commit && vm.canStep(dir)) {
                            scope.launch {
                                Animatable(total).animateTo(if (dir > 0) -h else h, tween(180)) {
                                    setOffset(value)
                                }
                                vm.step(dir)
                                setOffset(0f)
                            }
                        } else {
                            if (commit) {
                                vm.toast(if (dir > 0) "已经是最后一集了" else "已经是第 1 集了")
                            }
                            scope.launch {
                                Animatable(total).animateTo(0f, tween(160)) { setOffset(value) }
                            }
                        }
                    },
                    onDragCancel = {
                        tracker = null
                        scope.launch {
                            Animatable(total).animateTo(0f, tween(160)) { setOffset(value) }
                        }
                    },
                )
            }
    )
}

@Composable
private fun TopChrome(
    state: UiState,
    vm: PlayerViewModel,
    topInset: androidx.compose.ui.unit.Dp,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Stage60,
                    1f to Color.Transparent,
                )
            )
            .padding(top = topInset),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = SpaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            IconBtn(Icons.Filled.Search, "搜索", active = state.libraryTab == LibraryTab.Search) {
                vm.openLibrary(LibraryTab.Search)
            }
            IconBtn(Icons.Filled.History, "观看历史", active = state.libraryTab == LibraryTab.History) {
                vm.openLibrary(LibraryTab.History)
            }
            IconBtn(
                if (state.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                "收藏夹",
                active = state.favorite,
            ) { vm.openLibrary(LibraryTab.Favorites) }
            IconBtn(Icons.Filled.Settings, "设置") { vm.openSettings() }
        }

        if (state.mode == PlayerMode.Feed) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (ch in CHANNELS) {
                    val selected = ch == state.channel
                    Column(
                        Modifier
                            .height(32.dp)
                            .clickable { vm.selectChannel(ch) }
                            .padding(horizontal = SpaceLg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            ch,
                            color = if (selected) Subtitle else Aside,
                            fontSize = 13.sp,
                        )
                        Box(
                            Modifier
                                .padding(top = 2.dp)
                                .width(if (selected) 20.dp else 0.dp)
                                .height(2.dp)
                                .background(Crabapple)
                        )
                    }
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(horizontal = SpaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "返回信息流", size = 32.dp) {
                    vm.exitSeriesMode()
                }
                Text(
                    (state.drama?.title.orEmpty()) +
                        if (state.episodeCount > 0) "  ·  第 ${state.episode?.number ?: 1} 集 / 共 ${state.episodeCount} 集" else "",
                    color = Subtitle,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = SpaceMd),
                )
            }
        }
        if (state.offline) OfflineBanner()
    }
}

@Composable
private fun OfflineBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Wing.copy(alpha = 0.92f))
            .padding(horizontal = SpaceMd, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Danger)
        )
        Spacer(Modifier.width(SpaceSm))
        Text("网络未连接 · 当前显示缓存内容", color = Subtitle70, fontSize = 11.sp)
    }
}

@Composable
private fun DramaInfo(state: UiState, onShowAll: () -> Unit) {
    val drama = state.drama ?: return
    Column(Modifier.fillMaxWidth()) {
        Text(
            drama.title,
            color = Subtitle,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (drama.description.isNotBlank()) {
            Text(
                drama.description,
                color = Subtitle70,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "第 ${state.episode?.number ?: 1} 集 / 共 ${state.episodeCount} 集",
                color = Aside,
                fontSize = 12.sp,
            )
            Spacer(Modifier.width(SpaceSm))
            Text(
                "进入查看全剧 ›",
                color = Crabapple,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(onClick = onShowAll)
                    .padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun SeekBar(position: Double, duration: Double, onSeek: (Double) -> Unit) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val safeDuration = duration.coerceAtLeast(0.0)
    val fraction = dragFraction ?: if (safeDuration > 0) (position / safeDuration).coerceIn(0.0, 1.0).toFloat() else 0f

    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(safeDuration) {
                if (safeDuration <= 0) return@pointerInput
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f) * safeDuration)
                }
            }
            .pointerInput(safeDuration) {
                if (safeDuration <= 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFraction?.let { onSeek(it * safeDuration) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2
            val track = 3.dp.toPx()
            val thumb = 5.dp.toPx()
            drawLine(Subtitle25, Offset(0f, cy), Offset(size.width, cy), track, StrokeCap.Round)
            val x = fraction * size.width
            drawLine(Crabapple, Offset(0f, cy), Offset(x, cy), track, StrokeCap.Round)
            drawCircle(Subtitle, radius = thumb, center = Offset(x.coerceIn(thumb, size.width - thumb), cy))
        }
    }
}

@Composable
private fun ControlRow(
    state: UiState,
    prefs: Prefs,
    paused: Boolean,
    timePos: Double,
    duration: Double,
    speedMenu: Boolean,
    qualityMenu: Boolean,
    onSpeedMenu: (Boolean) -> Unit,
    onQualityMenu: (Boolean) -> Unit,
    vm: PlayerViewModel,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, "播放 / 暂停", size = 36.dp) {
            vm.togglePlay()
        }
        Text(
            "${formatClock(timePos)} / ${formatClock(duration)}",
            color = Subtitle70,
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))

        IconBtn(
            if (state.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            "收藏",
            active = state.favorite,
            size = 34.dp,
        ) { vm.toggleFavorite() }
        IconBtn(
            Icons.Filled.Subtitles,
            "弹幕",
            active = prefs.danmaku,
            size = 34.dp,
        ) { vm.setDanmaku(!prefs.danmaku) }
        IconBtn(Icons.Filled.GridView, "选集", active = state.sheetOpen, size = 34.dp) {
            vm.toggleSheet()
        }
        Box {
            TextBtn("${formatSpeed(vm.currentSpeed())}×", active = speedMenu) { onSpeedMenu(true) }
            DropdownMenu(
                expanded = speedMenu,
                onDismissRequest = { onSpeedMenu(false) },
                containerColor = Proscenium,
            ) {
                for (s in vm.availableSpeeds()) {
                    DropdownMenuItem(
                        text = { Text("${formatSpeed(s)}×", color = Subtitle) },
                        onClick = {
                            onSpeedMenu(false)
                            vm.setSpeed(s)
                        },
                    )
                }
            }
        }
        Box {
            TextBtn(state.quality, active = qualityMenu) { onQualityMenu(true) }
            DropdownMenu(
                expanded = qualityMenu,
                onDismissRequest = { onQualityMenu(false) },
                containerColor = Proscenium,
            ) {
                for (q in listOf("自动", "1080P", "720P", "480P")) {
                    DropdownMenuItem(
                        text = { Text(q, color = if (q == state.quality) Crabapple else Subtitle) },
                        onClick = {
                            onQualityMenu(false)
                            vm.setQuality(q)
                        },
                    )
                }
            }
        }
        IconBtn(
            if (vm.isMuted()) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
            "静音",
            size = 34.dp,
        ) { vm.toggleMute() }
    }
}

private fun formatSpeed(v: Double): String =
    if (v == v.toInt().toDouble()) "${v.toInt()}.0" else v.toString()

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(horizontal = SpaceXl)
            .clip(RoundedCornerShape(RadiusLg))
            .background(Scrim)
            .padding(horizontal = SpaceXl, vertical = SpaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("播放遇到问题", color = Subtitle, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(SpaceSm))
        Text(message, color = Aside, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(SpaceLg))
        Box(
            Modifier
                .clip(PillShape)
                .background(Crabapple)
                .clickable(onClick = onRetry)
                .padding(horizontal = SpaceXl, vertical = SpaceSm),
        ) {
            Text("重试", color = Subtitle, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EndPanel(state: UiState, onReplay: () -> Unit, onExit: () -> Unit) {
    val inSeries = state.mode == PlayerMode.Series
    Box(
        Modifier
            .fillMaxSize()
            .background(Scrim)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = SpaceXl),
        ) {
            Text("全剧终", color = Subtitle, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(SpaceSm))
            Text("你看完了全部 ${state.episodeCount} 集", color = Aside, fontSize = 12.sp)
            Spacer(Modifier.height(SpaceXl))
            Row(
                Modifier
                    .clip(PillShape)
                    .background(Crabapple)
                    .clickable(onClick = onReplay)
                    .padding(horizontal = SpaceXl, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(
                    Icons.Filled.Refresh,
                    null,
                    tint = Subtitle,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(SpaceSm))
                Text("从第 1 集重看", color = Subtitle, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(SpaceMd))
            if (inSeries) {
                Text(
                    "返回信息流",
                    color = Aside,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable(onClick = onExit)
                        .padding(SpaceSm),
                )
            } else {
                Text("上下滑动换一部短剧", color = Aside, fontSize = 12.sp)
            }
        }
    }
}