package com.ihaveapple.app.player

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ihaveapple.app.IhaApplication
import com.ihaveapple.app.data.DanmakuItem
import com.ihaveapple.app.data.Drama
import com.ihaveapple.app.data.EpisodeInfo
import com.ihaveapple.app.data.Playback
import com.ihaveapple.app.data.Prefs
import com.ihaveapple.app.data.ProgressRecord
import com.ihaveapple.app.data.StreamOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlayerMode { Feed, Series }

enum class LibraryTab { History, Favorites, Search }

data class UiState(
    val booted: Boolean = false,
    val channel: String = "推荐",
    val dramas: List<Drama> = emptyList(),
    val feedIndex: Int = 0,
    val feedCursor: String = "",
    val feedHasMore: Boolean = false,
    val mode: PlayerMode = PlayerMode.Feed,
    val drama: Drama? = null,
    val episodes: List<EpisodeInfo> = emptyList(),
    val episodeIndex: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val channelEmpty: Boolean = false,
    val offline: Boolean = false,
    val ended: Boolean = false,
    val heldAtEnd: Boolean = false,
    val controlsVisible: Boolean = true,
    val sheetOpen: Boolean = false,
    val settingsOpen: Boolean = false,
    val libraryTab: LibraryTab? = null,
    val toast: String? = null,
    val toastKey: Long = 0L,
    val titleCard: String? = null,
    val titleCardKey: Long = 0L,
    val favorite: Boolean = false,
    val quality: String = "自动",
    val estimateDuration: Double = 0.0,
    val history: List<ProgressRecord> = emptyList(),
    val favorites: List<ProgressRecord> = emptyList(),
    val searches: List<String> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Drama> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
) {
    val episode: EpisodeInfo? get() = episodes.getOrNull(episodeIndex)
    val episodeCount: Int get() = if (episodes.isNotEmpty()) episodes.size else drama?.episodeCount ?: 0
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as IhaApplication).container
    private val store = container.store
    private val repo = container.repository
    val mpv = MpvController(app)

    val prefs: StateFlow<Prefs> = store.prefs

    private val _ui = MutableStateFlow(UiState(channel = store.prefs.value.channel, quality = store.prefs.value.quality))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _danmaku = MutableStateFlow<List<DanmakuItem>>(emptyList())
    val danmaku: StateFlow<List<DanmakuItem>> = _danmaku.asStateFlow()

    private var playJob: Job? = null
    private var feedJob: Job? = null
    private var moreJob: Job? = null
    private var searchJob: Job? = null
    private var controlsJob: Job? = null
    private var toastJob: Job? = null
    private var titleJob: Job? = null
    private var timeoutJob: Job? = null
    private var danmakuJob: Job? = null

    private var currentPlayback: Playback? = null
    private var currentStream: StreamOption? = null
    private var lastSaveElapsed = 0L
    private var pausedByBackground = false

    private val speeds = listOf(0.75, 1.0, 1.25, 1.5, 2.0)
    private var speed = 1.0
    private var volume = 100
    private var muted = false

    init {
        mpv.init()
        viewModelScope.launch { bootstrap() }

        viewModelScope.launch {
            mpv.loaded.collect { loaded ->
                if (loaded) {
                    timeoutJob?.cancel()
                    _ui.update { it.copy(loading = false, error = null) }
                    scheduleHide()
                }
            }
        }

        viewModelScope.launch {
            mpv.paused.collect { paused ->
                if (paused) {
                    controlsJob?.cancel()
                    _ui.update { it.copy(controlsVisible = true) }
                } else if (!_ui.value.loading) {
                    scheduleHide()
                }
            }
        }

        viewModelScope.launch {
            mpv.failed.collect { msg ->
                timeoutJob?.cancel()
                _ui.update { it.copy(loading = false, error = msg, controlsVisible = true) }
            }
        }

        viewModelScope.launch {
            mpv.ended.collect { onEpisodeEnded() }
        }

        viewModelScope.launch {
            mpv.timePos.collect { pos ->
                if (!mpv.loaded.value) return@collect
                val now = SystemClock.elapsedRealtime()
                if (now - lastSaveElapsed >= 5000) {
                    lastSaveElapsed = now
                    saveProgress(pos)
                }
            }
        }

        viewModelScope.launch {
            store.prefs.collect { p ->
                _ui.update { it.copy(quality = p.quality) }
            }
        }
    }

    // ---------- 启动 / 频道 ----------

    private suspend fun bootstrap() {
        val channel = store.prefs.value.channel
        val cached = store.loadFeed(channel, repo.feedDir)
        if (cached != null && cached.items.isNotEmpty()) {
            _ui.update {
                it.copy(
                    channel = channel,
                    dramas = cached.items,
                    feedCursor = cached.cursor,
                    feedHasMore = cached.hasMore,
                )
            }
            val idx = pickInitialIndex(cached.items)
            openDrama(cached.items[idx], idx, seriesMode = false)
            refreshFeed(silent = true)
        } else {
            refreshFeed(silent = false)
        }
    }

    private fun pickInitialIndex(list: List<Drama>): Int {
        if (list.size <= 1) return 0
        val last = store.prefs.value.lastRecommendId
        val i = list.indexOfFirst { it.id == last }
        return if (i == 0) 1 else 0
    }

    private fun refreshFeed(silent: Boolean) {
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            try {
                val page = repo.feedPage(_ui.value.channel, null)
                store.saveFeed(_ui.value.channel, repo.feedDir, page)
                val current = _ui.value.drama
                val list = if (current != null && page.items.none { it.id == current.id }) {
                    listOf(current) + page.items
                } else page.items
                _ui.update {
                    it.copy(
                        dramas = list,
                        feedCursor = page.cursor,
                        feedHasMore = page.hasMore,
                        offline = false,
                        channelEmpty = list.isEmpty(),
                        feedIndex = list.indexOfFirst { d -> d.id == current?.id }.coerceAtLeast(0),
                    )
                }
                if (_ui.value.drama == null && list.isNotEmpty()) {
                    val idx = pickInitialIndex(list)
                    openDrama(list[idx], idx, seriesMode = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_ui.value.dramas.isEmpty()) {
                    _ui.update {
                        it.copy(
                            loading = false,
                            error = null,
                            channelEmpty = true,
                            offline = !silent,
                        )
                    }
                } else {
                    _ui.update { it.copy(offline = true) }
                }
            }
        }
    }

    fun selectChannel(channel: String) {
        if (channel == _ui.value.channel) {
            shuffleFeed()
            return
        }
        saveProgress()
        playJob?.cancel()
        feedJob?.cancel()
        moreJob?.cancel()
        mpv.stop()
        currentPlayback = null
        currentStream = null
        _danmaku.value = emptyList()
        store.updatePrefs { it.copy(channel = channel) }
        _ui.update {
            it.copy(
                channel = channel,
                mode = PlayerMode.Feed,
                drama = null,
                dramas = emptyList(),
                feedIndex = 0,
                feedCursor = "",
                feedHasMore = false,
                episodes = emptyList(),
                episodeIndex = 0,
                loading = true,
                error = null,
                ended = false,
                heldAtEnd = false,
                channelEmpty = false,
                sheetOpen = false,
                settingsOpen = false,
                libraryTab = null,
                controlsVisible = true,
            )
        }
        val cached = store.loadFeed(channel, repo.feedDir)
        if (cached != null && cached.items.isNotEmpty()) {
            _ui.update {
                it.copy(
                    dramas = cached.items,
                    feedCursor = cached.cursor,
                    feedHasMore = cached.hasMore,
                )
            }
            val idx = pickInitialIndex(cached.items)
            openDrama(cached.items[idx], idx, seriesMode = false)
            refreshFeed(silent = true)
        } else {
            refreshFeed(silent = false)
        }
    }

    fun shuffleFeed() {
        val cur = _ui.value.drama
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            try {
                val list = repo.reshuffle(_ui.value.channel, cur?.id ?: "")
                if (list.isEmpty()) {
                    toast("「${_ui.value.channel}」暂时没有内容")
                    return@launch
                }
                _ui.update { it.copy(dramas = list, feedIndex = 0, offline = false) }
                openDrama(list[0], 0, seriesMode = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val local = _ui.value.dramas.filter { it.id != cur?.id }.shuffled()
                if (local.isEmpty()) {
                    toast("换一批失败，请稍后再试")
                } else {
                    _ui.update { it.copy(dramas = local, feedIndex = 0) }
                    openDrama(local[0], 0, seriesMode = false)
                }
            }
        }
    }

    fun retry() {
        if (_ui.value.dramas.isEmpty()) {
            _ui.update { it.copy(loading = true, error = null, channelEmpty = false) }
            refreshFeed(silent = false)
        } else {
            val d = _ui.value.drama
            if (d != null) openDrama(d, _ui.value.feedIndex, seriesMode = _ui.value.mode == PlayerMode.Series)
            else refreshFeed(silent = false)
        }
    }

    // ---------- 打开剧 / 集 ----------

    fun openDrama(drama: Drama, index: Int = -1, seriesMode: Boolean = false, record: ProgressRecord? = null) {
        saveProgress()
        playJob?.cancel()
        timeoutJob?.cancel()
        danmakuJob?.cancel()
        mpv.stop()
        currentPlayback = null
        currentStream = null
        _danmaku.value = emptyList()
        val fav = store.record(drama.id)?.favorite ?: false
        val feedIndex = if (index >= 0) index else _ui.value.dramas.indexOfFirst { it.id == drama.id }.coerceAtLeast(0)
        _ui.update {
            it.copy(
                drama = drama,
                feedIndex = feedIndex,
                mode = if (seriesMode) PlayerMode.Series else PlayerMode.Feed,
                episodes = emptyList(),
                episodeIndex = 0,
                loading = true,
                error = null,
                ended = false,
                heldAtEnd = false,
                sheetOpen = false,
                settingsOpen = false,
                libraryTab = null,
                favorite = fav,
                titleCard = null,
                estimateDuration = 0.0,
                controlsVisible = true,
            )
        }
        store.updatePrefs { it.copy(lastRecommendId = drama.id) }
        if (seriesMode) {
            store.markListed(drama.id)
            store.saveProgress(
                seriesId = drama.id,
                title = drama.title,
                total = drama.episodeCount,
                coverUrl = drama.coverUrl,
                episodeNumber = record?.episodeNumber ?: store.record(drama.id)?.episodeNumber ?: 1,
                positionSec = record?.positionSec ?: store.record(drama.id)?.positionSec ?: 0.0,
                durationSec = record?.durationSec ?: store.record(drama.id)?.durationSec ?: 0.0,
                listed = true,
            )
        }
        playJob = viewModelScope.launch {
            try {
                val eps = repo.episodes(drama.id)
                if (eps.isEmpty()) {
                    _ui.update { it.copy(loading = false, error = "这部剧暂时没有可播放的分集") }
                    return@launch
                }
                val start = resolveStart(drama, eps, record)
                _ui.update { it.copy(episodes = eps, episodeIndex = start.first) }
                playEpisode(drama, eps, start.first, start.second)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = messageOf(e)) }
            }
        }
    }

    private fun resolveStart(drama: Drama, eps: List<EpisodeInfo>, record: ProgressRecord?): Pair<Int, Double> {
        val p = record ?: store.record(drama.id) ?: return 0 to 0.0
        var idx = (p.episodeNumber - 1).coerceIn(0, eps.lastIndex)
        if (eps.isEmpty()) idx = 0
        if (p.durationSec > 0 && p.positionSec >= p.durationSec * 0.95 && idx + 1 < eps.size) {
            return idx + 1 to 0.0
        }
        return idx to p.positionSec
    }

    private suspend fun playEpisode(drama: Drama, eps: List<EpisodeInfo>, index: Int, startSec: Double) {
        val ep = eps.getOrNull(index) ?: return
        _ui.update {
            it.copy(
                episodeIndex = index,
                loading = true,
                error = null,
                ended = false,
                heldAtEnd = false,
                estimateDuration = 0.0,
                controlsVisible = true,
                titleCard = null,
            )
        }
        Log.d("player", "play drama=${drama.id} episode=${ep.id} #${ep.number}")
        val pb = repo.resolve(ep.id)
        val stream = pb.pick(prefs.value.quality)
        if (stream == null) {
            _ui.update { it.copy(loading = false, error = "这一集没有可用的单文件清晰度") }
            return
        }
        if (pb.protected && pb.keyHex.isNullOrBlank()) {
            _ui.update { it.copy(loading = false, error = "这一集缺少内容密钥，暂时无法播放") }
            return
        }
        currentPlayback = pb
        currentStream = stream
        _ui.update { it.copy(estimateDuration = pb.durationSec.toDouble()) }
        mpv.load(stream.url, pb.keyHex, startSec, autoPlay = true)
        showTitleCard(ep.number)
        startLoadTimeout()
        prefetch(eps.getOrNull(index + 1))
        loadDanmaku(drama.id, ep.id)
        _ui.update { it.copy(favorite = store.record(drama.id)?.favorite ?: false) }
    }

    private fun prefetch(ep: EpisodeInfo?) {
        if (ep == null) return
        viewModelScope.launch {
            try {
                repo.resolve(ep.id)
            } catch (_: Exception) {
            }
        }
    }

    private fun startLoadTimeout() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(25_000)
            if (!mpv.loaded.value && _ui.value.loading && _ui.value.error == null) {
                _ui.update { it.copy(loading = false, error = "加载超时，请检查网络后重试") }
            }
        }
    }

    private fun loadDanmaku(seriesId: String, episodeId: String) {
        danmakuJob?.cancel()
        _danmaku.value = emptyList()
        if (!prefs.value.danmaku) return
        danmakuJob = viewModelScope.launch {
            try {
                _danmaku.value = repo.danmaku(seriesId, episodeId)
            } catch (_: Exception) {
            }
        }
    }

    fun goToEpisode(index: Int, startSec: Double = 0.0, enterSeries: Boolean = true) {
        val state = _ui.value
        val drama = state.drama ?: return
        val eps = state.episodes
        if (index !in eps.indices) return
        saveProgress()
        playJob?.cancel()
        if (enterSeries) {
            _ui.update { it.copy(mode = PlayerMode.Series) }
            store.markListed(drama.id)
        }
        playJob = viewModelScope.launch {
            try {
                playEpisode(drama, eps, index, startSec)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = messageOf(e)) }
            }
        }
    }

    fun selectEpisode(number: Int) {
        val eps = _ui.value.episodes
        val idx = eps.indexOfFirst { it.number == number }
        if (idx >= 0) goToEpisode(idx)
    }

    // ---------- 滑动 ----------

    fun canStep(dir: Int): Boolean {
        val state = _ui.value
        if (state.sheetOpen || state.settingsOpen || state.libraryTab != null) return false
        return if (state.mode == PlayerMode.Series) {
            state.episodes.isNotEmpty() && state.episodeIndex + dir in state.episodes.indices
        } else {
            state.dramas.size >= 2 || state.feedHasMore
        }
    }

    fun step(dir: Int) {
        val state = _ui.value
        if (state.sheetOpen || state.settingsOpen || state.libraryTab != null) return
        if (state.mode == PlayerMode.Series) stepEpisode(dir) else stepDrama(dir)
    }

    private fun stepEpisode(dir: Int) {
        val state = _ui.value
        val next = state.episodeIndex + dir
        if (state.episodes.isEmpty()) return
        if (next !in state.episodes.indices) {
            toast(if (dir < 0) "已经是第 1 集了" else "已经是最后一集了")
            return
        }
        goToEpisode(next, 0.0, enterSeries = true)
    }

    private fun stepDrama(dir: Int) {
        val state = _ui.value
        val list = state.dramas
        if (list.isEmpty()) return
        if (list.size == 1 && !state.feedHasMore) {
            toast("当前频道只有这一部")
            return
        }
        saveProgress()
        var next = state.feedIndex + dir
        if (next < 0) next = list.lastIndex
        if (next > list.lastIndex) next = 0
        val target = list[next]
        openDrama(target, next, seriesMode = false)
        maybeLoadMore(next)
    }

    private fun maybeLoadMore(index: Int) {
        val state = _ui.value
        if (!state.feedHasMore || state.feedCursor.isBlank()) return
        if (index < state.dramas.size - 3) return
        if (moreJob?.isActive == true) return
        moreJob = viewModelScope.launch {
            try {
                val page = repo.feedPage(state.channel, state.feedCursor)
                if (page.items.isEmpty()) {
                    _ui.update { it.copy(feedHasMore = false) }
                    return@launch
                }
                _ui.update {
                    val merged = (it.dramas + page.items).distinctBy { d -> d.id }
                    it.copy(dramas = merged, feedCursor = page.cursor, feedHasMore = page.hasMore)
                }
            } catch (_: Exception) {
            }
        }
    }

    // ---------- 播放控制 ----------

    fun togglePlay() {
        val state = _ui.value
        if (state.ended || state.loading || state.error != null) return
        if (state.heldAtEnd) {
            _ui.update { it.copy(heldAtEnd = false) }
            goToEpisode(state.episodeIndex + 1, 0.0, enterSeries = state.mode == PlayerMode.Series)
            return
        }
        mpv.togglePause()
        showControls()
    }

    fun seekTo(sec: Double) {
        if (!mpv.loaded.value) return
        mpv.seekTo(sec)
        _ui.update { it.copy(ended = false, heldAtEnd = false) }
    }

    fun enterSeriesMode() {
        val drama = _ui.value.drama ?: return
        _ui.update { it.copy(mode = PlayerMode.Series) }
        store.markListed(drama.id)
        saveProgress()
    }

    fun exitSeriesMode() {
        _ui.update { it.copy(mode = PlayerMode.Feed) }
    }

    fun replay() {
        _ui.update { it.copy(ended = false) }
        goToEpisode(0, 0.0, enterSeries = _ui.value.mode == PlayerMode.Series)
    }

    fun toggleFavorite() {
        val state = _ui.value
        val drama = state.drama ?: return
        val epNumber = state.episode?.number ?: 1
        val pos = mpv.timePos.value
        val dur = durationNow()
        storeMarkListedIf(state.mode == PlayerMode.Series, drama, state.episodeCount, epNumber, pos, dur)
        val rec = store.record(drama.id)
        val next = !(rec?.favorite ?: false)
        store.setFavorite(
            seriesId = drama.id,
            favorite = next,
            snapshot = rec ?: ProgressRecord(
                seriesId = drama.id,
                title = drama.title,
                total = state.episodeCount,
                episodeNumber = epNumber,
                positionSec = pos,
                durationSec = dur,
                coverUrl = drama.coverUrl,
                updatedAt = System.currentTimeMillis(),
                listed = state.mode == PlayerMode.Series,
                favorite = false,
                favoritedAt = 0L,
            ),
        )
        _ui.update { it.copy(favorite = next) }
        toast(if (next) "已收藏，可在收藏夹里续播" else "已取消收藏")
    }

    fun removeFavorite(seriesId: String) {
        val rec = store.record(seriesId) ?: return
        store.setFavorite(seriesId, false, snapshot = rec)
        _ui.update {
            it.copy(
                favorites = store.favorites(),
                favorite = if (it.drama?.id == seriesId) false else it.favorite,
            )
        }
        toast("已取消收藏")
    }

    fun setQuality(quality: String) {
        store.updatePrefs { it.copy(quality = quality) }
        val pb = currentPlayback
        if (pb == null) {
            toast("画质 $quality")
            return
        }
        val stream = pb.pick(quality) ?: return
        if (stream.url == currentStream?.url) {
            toast("画质 $quality")
            return
        }
        val pos = mpv.timePos.value
        val playing = !mpv.paused.value
        currentStream = stream
        mpv.load(stream.url, pb.keyHex, pos, autoPlay = playing)
        showToast("画质 $quality")
    }

    fun setSpeed(value: Double) {
        speed = value
        mpv.setSpeed(value)
        showToast("倍速 ${if (value == value.toInt().toDouble()) "${value.toInt()}.0×" else "$value×"}")
    }

    fun toggleMute() {
        muted = !muted
        mpv.setMute(muted)
        showToast(if (muted) "已静音" else "已取消静音")
    }

    fun adjustVolume(delta: Int) {
        volume = (volume + delta).coerceIn(0, 100)
        muted = false
        mpv.setMute(false)
        mpv.setVolume(volume)
        showToast("音量 $volume%")
    }

    fun availableSpeeds(): List<Double> = speeds
    fun currentSpeed(): Double = speed
    fun isMuted(): Boolean = muted

    fun showControls() {
        _ui.update { it.copy(controlsVisible = true) }
        scheduleHide()
    }

    private fun scheduleHide() {
        controlsJob?.cancel()
        val state = _ui.value
        if (mpv.paused.value || state.loading || state.error != null || state.ended ||
            state.sheetOpen || state.settingsOpen || state.libraryTab != null
        ) return
        controlsJob = viewModelScope.launch {
            delay(prefs.value.controlsIdleMs.toLong())
            if (!mpv.paused.value && !_ui.value.loading) {
                _ui.update { it.copy(controlsVisible = false) }
            }
        }
    }

    // ---------- 播放结束 ----------

    private fun onEpisodeEnded() {
        val state = _ui.value
        if (state.ended) return
        if (state.episodes.isEmpty()) return
        saveProgressAtEnd()
        if (state.episodeIndex + 1 < state.episodes.size) {
            if (prefs.value.autoNext) {
                goToEpisode(state.episodeIndex + 1, 0.0, enterSeries = state.mode == PlayerMode.Series)
            } else {
                mpv.setPause(true)
                _ui.update { it.copy(heldAtEnd = true, controlsVisible = true) }
                toast("本集已播完，按播放继续下一集")
            }
        } else {
            mpv.setPause(true)
            _ui.update { it.copy(ended = true, controlsVisible = true) }
        }
    }

    // ---------- 选集 / 面板 ----------

    fun toggleSheet() {
        if (_ui.value.sheetOpen) {
            _ui.update { it.copy(sheetOpen = false) }
            scheduleHide()
            return
        }
        enterSeriesMode()
        controlsJob?.cancel()
        _ui.update { it.copy(sheetOpen = true, controlsVisible = true, settingsOpen = false, libraryTab = null) }
    }

    fun closeSheet() {
        _ui.update { it.copy(sheetOpen = false) }
        scheduleHide()
    }

    fun openSettings() {
        controlsJob?.cancel()
        _ui.update { it.copy(settingsOpen = true, libraryTab = null, sheetOpen = false, controlsVisible = true) }
    }

    fun closeSettings() {
        _ui.update { it.copy(settingsOpen = false) }
        scheduleHide()
    }

    fun openLibrary(tab: LibraryTab) {
        controlsJob?.cancel()
        _ui.update {
            it.copy(
                libraryTab = tab,
                settingsOpen = false,
                sheetOpen = false,
                controlsVisible = true,
                history = store.history(),
                favorites = store.favorites(),
                searches = store.searches(),
                searchError = null,
                searchQuery = if (tab == LibraryTab.Search) it.searchQuery else "",
                searchResults = if (tab == LibraryTab.Search) it.searchResults else emptyList(),
            )
        }
    }

    fun closeLibrary() {
        _ui.update { it.copy(libraryTab = null) }
        scheduleHide()
    }

    fun openRecord(record: ProgressRecord) {
        val drama = Drama(
            id = record.seriesId,
            title = record.title,
            coverUrl = record.coverUrl,
            episodeCount = record.total,
        )
        openDrama(drama, -1, seriesMode = true, record = record)
    }

    fun openSearchResult(drama: Drama) {
        openDrama(drama, -1, seriesMode = true)
    }

    fun submitSearch(query: String) {
        val q = query.trim()
        _ui.update { it.copy(searchQuery = q, searchError = null) }
        if (q.isEmpty()) {
            _ui.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        store.addSearch(q)
        searchJob?.cancel()
        _ui.update { it.copy(searching = true, searches = store.searches()) }
        searchJob = viewModelScope.launch {
            try {
                val list = repo.search(q)
                _ui.update { it.copy(searchResults = list, searching = false, searchError = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(searching = false, searchError = messageOf(e)) }
            }
        }
    }

    fun clearSearches() {
        store.clearSearches()
        _ui.update { it.copy(searches = emptyList()) }
    }

    fun clearHistory() {
        store.clearHistory()
        _ui.update { it.copy(history = emptyList()) }
    }

    fun clearFavorites() {
        store.clearFavorites()
        _ui.update { it.copy(favorites = emptyList(), favorite = false) }
    }

    // ---------- 设置 ----------

    fun setAutoNext(value: Boolean) = store.updatePrefs { it.copy(autoNext = value) }.let { }
    fun setTitleCard(value: Boolean) = store.updatePrefs { it.copy(titleCard = value) }.let { }
    fun setDanmaku(value: Boolean) {
        store.updatePrefs { it.copy(danmaku = value) }
        val state = _ui.value
        val drama = state.drama
        val ep = state.episode
        if (value && drama != null && ep != null) loadDanmaku(drama.id, ep.id) else _danmaku.value = emptyList()
    }

    fun setKeepAwake(value: Boolean) = store.updatePrefs { it.copy(keepAwake = value) }.let { }
    fun setPauseOnBackground(value: Boolean) = store.updatePrefs { it.copy(pauseOnBackground = value) }.let { }
    fun setControlsIdle(ms: Int) = store.updatePrefs { it.copy(controlsIdleMs = ms) }.let { }

    fun resetAll() {
        store.resetAll()
        store.clearFeeds(repo.feedDir)
        mpv.stop()
        currentPlayback = null
        _danmaku.value = emptyList()
        _ui.value = UiState(channel = store.prefs.value.channel, quality = store.prefs.value.quality)
        viewModelScope.launch { bootstrap() }
        toast("已恢复默认设置")
    }

    // ---------- 生命周期 ----------

    fun onEnterBackground() {
        pausedByBackground = prefs.value.pauseOnBackground && mpv.loaded.value && !mpv.paused.value
        if (pausedByBackground) {
            mpv.setPause(true)
            saveProgress()
        }
    }

    fun onEnterForeground() {
        if (pausedByBackground) {
            pausedByBackground = false
            mpv.setPause(false)
        }
    }

    /** 处理返回键；返回 true 表示消费了事件。 */
    fun handleBack(): Boolean {
        val state = _ui.value
        return when {
            state.settingsOpen -> { closeSettings(); true }
            state.libraryTab != null -> { closeLibrary(); true }
            state.sheetOpen -> { closeSheet(); true }
            state.ended -> { _ui.update { it.copy(ended = false) }; true }
            state.mode == PlayerMode.Series -> { exitSeriesMode(); true }
            else -> false
        }
    }

    // ---------- 进度 / 提示 ----------

    private fun durationNow(): Double {
        val d = mpv.duration.value
        if (d > 1) return d
        val est = _ui.value.estimateDuration
        return if (est > 1) est else 0.0
    }

    private fun saveProgress(position: Double = mpv.timePos.value) {
        val state = _ui.value
        val drama = state.drama ?: return
        if (state.episodes.isEmpty()) return
        val ep = state.episode ?: return
        val dur = durationNow()
        if (dur <= 0 && position <= 0) return
        store.saveProgress(
            seriesId = drama.id,
            title = drama.title,
            total = if (state.episodes.isNotEmpty()) state.episodes.size else drama.episodeCount,
            coverUrl = drama.coverUrl,
            episodeNumber = ep.number,
            positionSec = position,
            durationSec = dur,
            listed = state.mode == PlayerMode.Series,
        )
        lastSaveElapsed = SystemClock.elapsedRealtime()
    }

    private fun saveProgressAtEnd() {
        val dur = durationNow()
        saveProgress(if (dur > 0) dur else mpv.timePos.value)
    }

    private fun showTitleCard(number: Int) {
        if (!prefs.value.titleCard) return
        titleJob?.cancel()
        _ui.update { it.copy(titleCard = "第 $number 集", titleCardKey = System.currentTimeMillis()) }
        titleJob = viewModelScope.launch {
            delay(1200)
            _ui.update { it.copy(titleCard = null) }
        }
    }

    fun toast(message: String) = showToast(message)

    private fun showToast(message: String) {
        toastJob?.cancel()
        _ui.update { it.copy(toast = message, toastKey = System.currentTimeMillis()) }
        toastJob = viewModelScope.launch {
            delay(2000)
            _ui.update { it.copy(toast = null) }
        }
    }

    private fun messageOf(e: Exception): String {
        val raw = e.message?.takeIf { it.isNotBlank() } ?: "网络请求失败"
        return if (raw.length > 80) raw.take(80) + "…" else raw
    }

    private fun storeMarkListedIf(condition: Boolean, drama: Drama, total: Int, ep: Int, pos: Double, dur: Double) {
        if (condition) store.markListed(drama.id)
        store.saveProgress(
            seriesId = drama.id,
            title = drama.title,
            total = total,
            coverUrl = drama.coverUrl,
            episodeNumber = ep,
            positionSec = pos,
            durationSec = dur,
            listed = condition,
        )
    }

    override fun onCleared() {
        saveProgress()
        mpv.release()
        super.onCleared()
    }
}