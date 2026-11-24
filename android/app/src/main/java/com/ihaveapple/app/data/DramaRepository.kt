package com.ihaveapple.app.data

import android.content.Context
import android.util.Log
import dev.openhongguo.ClientOptions
import dev.openhongguo.DanmakuOptions
import dev.openhongguo.DiscoverOptions
import dev.openhongguo.EpisodeOptions
import dev.openhongguo.OpenhongguoClient
import dev.openhongguo.SearchOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 红果内容接入：发现流、搜索、分集、播放模型（含内容密钥）、弹幕。
 * 播放模型与分集列表在内存里缓存；信息流按频道落盘缓存一份，断网可先看。
 */
class DramaRepository(context: Context) {

    val feedDir: File = File(context.filesDir, "feed")

    private val client = OpenhongguoClient.create(
        context,
        ClientOptions(
            retryAttempts = 3,
            deviceResets = 2,
            logger = { line -> Log.d("openhongguo", line) },
        ),
    )

    private val episodesLock = Mutex()
    private val episodesCache = HashMap<String, List<EpisodeInfo>>()
    private val playbackCache = ConcurrentHashMap<String, Playback>()

    // ---------- 信息流 ----------

    suspend fun feedPage(channel: String, cursor: String?): FeedPage = withContext(Dispatchers.IO) {
        val page = client.discover(
            DiscoverOptions(
                cursor = cursor?.takeIf { it.isNotBlank() },
                channel = channel,
            )
        )
        val seen = HashSet<String>()
        val items = page.items.mapNotNull { s ->
            if (s.id.isBlank() || !seen.add(s.id)) null
            else Drama(
                id = s.id,
                title = s.title,
                description = s.description,
                coverUrl = s.coverUrl,
                episodeCount = s.episodeCount,
            )
        }
        FeedPage(items, page.cursor, page.hasMore)
    }

    suspend fun search(query: String): List<Drama> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val page = client.search(q, SearchOptions(pageSize = 30))
        page.items.distinctBy { it.id }.map {
            Drama(it.id, it.title, it.description, it.coverUrl, it.episodeCount)
        }
    }

    /** 豆瓣式的"再换一批"：向服务端要一页新的推荐，排除当前正在看的剧。 */
    suspend fun reshuffle(channel: String, excludeId: String): List<Drama> {
        val page = feedPage(channel, null)
        val list = page.items.filter { it.id != excludeId }.shuffled()
        return (list + page.items.filter { it.id == excludeId })
    }

    suspend fun drama(seriesId: String, fallback: Drama): Drama = withContext(Dispatchers.IO) {
        try {
            val s = client.getSeries(seriesId)
            Drama(
                id = s.id.ifBlank { fallback.id },
                title = s.title.ifBlank { fallback.title },
                description = s.description.ifBlank { fallback.description },
                coverUrl = s.coverUrl.ifBlank { fallback.coverUrl },
                episodeCount = if (s.episodeCount > 0) s.episodeCount else fallback.episodeCount,
            )
        } catch (e: Exception) {
            Log.w("repo", "getSeries $seriesId failed: ${e.message}")
            fallback
        }
    }

    // ---------- 分集 ----------

    suspend fun episodes(seriesId: String): List<EpisodeInfo> {
        episodesCache[seriesId]?.let { return it }
        return episodesLock.withLock {
            episodesCache[seriesId]?.let { return@withLock it }
            val all = mutableListOf<EpisodeInfo>()
            var cursor: String? = null
            var pages = 0
            do {
                val page = withContext(Dispatchers.IO) {
                    client.listEpisodes(
                        seriesId,
                        EpisodeOptions(cursor = cursor, pageSize = 100),
                    )
                }
                all += page.items.map {
                    EpisodeInfo(
                        id = it.id,
                        seriesId = it.seriesId.ifBlank { seriesId },
                        number = it.number,
                        title = it.title,
                        coverUrl = it.coverUrl,
                        playable = it.playable,
                    )
                }
                cursor = page.cursor.takeIf { page.hasMore && it.isNotBlank() }
                pages++
            } while (cursor != null && pages < 20)
            val list = all.distinctBy { it.id }.sortedBy { it.number }
            episodesCache[seriesId] = list
            list
        }
    }

    fun cachedEpisodes(seriesId: String): List<EpisodeInfo>? = episodesCache[seriesId]

    // ---------- 播放 ----------

    suspend fun resolve(episodeId: String, force: Boolean = false): Playback {
        val cached = playbackCache[episodeId]
        if (!force && cached != null && !isExpired(cached)) return cached
        val result = try {
            withContext(Dispatchers.IO) { client.resolvePlayback(episodeId) }
        } catch (e: Exception) {
            Log.w("repo", "resolve $episodeId failed: ${e.message}")
            throw e
        }
        val pb = result.playback
        Log.d(
            "repo",
            "resolve $episodeId -> ${pb.streams.size} streams, protected=${pb.protected}, key=${result.contentKey?.key?.isNotBlank() ?: false}",
        )
        val model = Playback(
            episodeId = episodeId,
            durationSec = pb.durationSeconds,
            protected = pb.protected,
            keyHex = result.contentKey?.key?.takeIf { it.isNotBlank() },
            streams = pb.streams.map {
                StreamOption(
                    definition = it.definition,
                    width = it.width,
                    height = it.height,
                    url = it.url,
                    hls = it.hls,
                    dash = it.dash,
                )
            },
            expiresAt = pb.expiresAt,
        )
        playbackCache[episodeId] = model
        return model
    }

    fun cachedPlayback(episodeId: String): Playback? = playbackCache[episodeId]?.takeIf { !isExpired(it) }

    private fun isExpired(pb: Playback): Boolean {
        if (pb.expiresAt <= 0) return false
        val nowSec = System.currentTimeMillis() / 1000
        return nowSec > pb.expiresAt - 120
    }

    // ---------- 弹幕 ----------

    suspend fun danmaku(seriesId: String, episodeId: String): List<DanmakuItem> = withContext(Dispatchers.IO) {
        val seen = HashSet<String>()
        val out = mutableListOf<DanmakuItem>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = client.listDanmaku(
                seriesId,
                episodeId,
                DanmakuOptions(cursor = cursor, startMs = if (cursor == null) 0L else null, pageSize = 90),
            )
            for (d in page.items) {
                if (d.text.isNotBlank() && seen.add(d.id)) {
                    out += DanmakuItem(d.id, d.text, d.offsetMs)
                }
            }
            cursor = page.cursor.takeIf { page.hasMore && it.isNotBlank() }
            pages++
        } while (cursor != null && pages < 6)
        out.sortedBy { it.offsetMs }
    }

    suspend fun resetDevice() = withContext(Dispatchers.IO) { client.resetDevice() }
}