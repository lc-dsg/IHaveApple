package com.ihaveapple.app.data

/** 一部短剧（信息流 / 搜索结果 / 资料页共用）。 */
data class Drama(
    val id: String,
    val title: String,
    val description: String = "",
    val coverUrl: String = "",
    val episodeCount: Int = 0,
)

data class EpisodeInfo(
    val id: String,
    val seriesId: String,
    val number: Int,
    val title: String = "",
    val durationSec: Int = 0,
    val coverUrl: String = "",
    val playable: Boolean = true,
)

data class StreamOption(
    val definition: String,
    val width: Int,
    val height: Int,
    val url: String,
    val hls: Boolean = false,
    val dash: Boolean = false,
)

data class Playback(
    val episodeId: String,
    val durationSec: Int,
    val protected: Boolean,
    val keyHex: String?,
    val streams: List<StreamOption>,
    /** 流地址的过期时间（unix 秒）；0 表示未知。 */
    val expiresAt: Long = 0L,
) {
    /** 按「自动 / 1080P / 720P / 480P」挑一档单文件流。 */
    fun pick(quality: String): StreamOption? {
        val candidates = streams.filter { !it.hls && !it.dash && it.url.isNotBlank() }
        if (candidates.isEmpty()) return null
        if (quality != "自动") {
            val wanted = quality.lowercase()
            candidates.firstOrNull { it.definition.equals(wanted, ignoreCase = true) }?.let { return it }
            val digits = wanted.removeSuffix("p")
            candidates.firstOrNull {
                it.definition.lowercase().removeSuffix("p") == digits
            }?.let { return it }
        }
        return candidates
            .sortedWith(compareByDescending<StreamOption> { if (it.height in 1..1080) it.height else 0 }
                .thenByDescending { it.height })
            .first()
    }
}

data class DanmakuItem(val id: String, val text: String, val offsetMs: Long)

data class Prefs(
    val channel: String = "推荐",
    val quality: String = "自动",
    val autoNext: Boolean = true,
    val titleCard: Boolean = true,
    val danmaku: Boolean = true,
    val keepAwake: Boolean = true,
    val pauseOnBackground: Boolean = true,
    val controlsIdleMs: Int = 2500,
    val lastRecommendId: String = "",
)

/** 观看进度 + 历史 / 收藏标记。listed 为真的才出现在观看历史里。 */
data class ProgressRecord(
    val seriesId: String,
    val title: String,
    val total: Int,
    val episodeNumber: Int,
    val positionSec: Double,
    val durationSec: Double,
    val coverUrl: String,
    val updatedAt: Long,
    val listed: Boolean,
    val favorite: Boolean,
    val favoritedAt: Long,
) {
    val progressPercent: Double
        get() = if (durationSec > 0) (positionSec / durationSec).coerceIn(0.0, 1.0) else 0.0
}

data class FeedPage(
    val items: List<Drama>,
    val cursor: String,
    val hasMore: Boolean,
)

data class FeedCache(
    val items: List<Drama>,
    val cursor: String,
    val hasMore: Boolean,
    val savedAt: Long,
)