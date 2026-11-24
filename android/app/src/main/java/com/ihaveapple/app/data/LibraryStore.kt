package com.ihaveapple.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 设置、观看进度、历史 / 收藏、搜索记录。全部落在 SharedPreferences 的 JSON 里，
 * 升级重装不丢；「完全重置」会一并清空。
 */
class LibraryStore(context: Context) {

    private val sp = context.getSharedPreferences("ihaveapple", Context.MODE_PRIVATE)
    private val lock = Any()

    private val _prefs = MutableStateFlow(loadPrefs())
    val prefs: StateFlow<Prefs> = _prefs.asStateFlow()

    fun updatePrefs(transform: (Prefs) -> Prefs): Prefs = synchronized(lock) {
        val next = transform(_prefs.value)
        _prefs.value = next
        sp.edit()
            .putString("channel", next.channel)
            .putString("quality", next.quality)
            .putBoolean("autoNext", next.autoNext)
            .putBoolean("titleCard", next.titleCard)
            .putBoolean("danmaku", next.danmaku)
            .putBoolean("keepAwake", next.keepAwake)
            .putBoolean("pauseOnBackground", next.pauseOnBackground)
            .putInt("controlsIdle", next.controlsIdleMs)
            .putString("lastRecommend", next.lastRecommendId)
            .apply()
        next
    }

    private fun loadPrefs(): Prefs = Prefs(
        channel = sp.getString("channel", "推荐") ?: "推荐",
        quality = sp.getString("quality", "自动") ?: "自动",
        autoNext = sp.getBoolean("autoNext", true),
        titleCard = sp.getBoolean("titleCard", true),
        danmaku = sp.getBoolean("danmaku", true),
        keepAwake = sp.getBoolean("keepAwake", true),
        pauseOnBackground = sp.getBoolean("pauseOnBackground", true),
        controlsIdleMs = sp.getInt("controlsIdle", 2500),
        lastRecommendId = sp.getString("lastRecommend", "") ?: "",
    )

    // ---------- 观看进度 ----------

    private fun progressJson(): JSONObject = try {
        JSONObject(sp.getString("progress", "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    private fun recordToJson(r: ProgressRecord): JSONObject = JSONObject().apply {
        put("id", r.seriesId)
        put("title", r.title)
        put("total", r.total)
        put("ep", r.episodeNumber)
        put("pos", r.positionSec)
        put("dur", r.durationSec)
        put("cover", r.coverUrl)
        put("at", r.updatedAt)
        put("listed", r.listed)
        put("fav", r.favorite)
        put("favAt", r.favoritedAt)
    }

    private fun jsonToRecord(o: JSONObject): ProgressRecord = ProgressRecord(
        seriesId = o.optString("id"),
        title = o.optString("title"),
        total = o.optInt("total"),
        episodeNumber = o.optInt("ep", 1),
        positionSec = o.optDouble("pos", 0.0),
        durationSec = o.optDouble("dur", 0.0),
        coverUrl = o.optString("cover"),
        updatedAt = o.optLong("at"),
        listed = o.optBoolean("listed"),
        favorite = o.optBoolean("fav"),
        favoritedAt = o.optLong("favAt"),
    )

    private fun keysOf(o: JSONObject): List<String> {
        val out = ArrayList<String>(o.length())
        val iterator = o.keys()
        while (iterator.hasNext()) out.add(iterator.next())
        return out
    }

    fun allRecords(): List<ProgressRecord> = synchronized(lock) {
        val obj = progressJson()
        buildList {
            for (key in keysOf(obj)) {
                obj.optJSONObject(key)?.let { add(jsonToRecord(it)) }
            }
        }
    }

    fun record(seriesId: String): ProgressRecord? = synchronized(lock) {
        progressJson().optJSONObject(seriesId)?.let { jsonToRecord(it) }
    }

    fun history(): List<ProgressRecord> = allRecords()
        .filter { it.listed }
        .sortedByDescending { it.updatedAt }

    fun favorites(): List<ProgressRecord> = allRecords()
        .filter { it.favorite }
        .sortedByDescending { it.favoritedAt }

    fun saveProgress(
        seriesId: String,
        title: String,
        total: Int,
        coverUrl: String,
        episodeNumber: Int,
        positionSec: Double,
        durationSec: Double,
        listed: Boolean? = null,
    ): ProgressRecord = synchronized(lock) {
        val obj = progressJson()
        val old = obj.optJSONObject(seriesId)?.let { jsonToRecord(it) }
        val now = System.currentTimeMillis()
        val rec = ProgressRecord(
            seriesId = seriesId,
            title = title.ifBlank { old?.title ?: "" },
            total = if (total > 0) total else old?.total ?: 0,
            episodeNumber = episodeNumber,
            positionSec = positionSec.coerceAtLeast(0.0),
            durationSec = durationSec.coerceAtLeast(0.0),
            coverUrl = coverUrl.ifBlank { old?.coverUrl ?: "" },
            updatedAt = now,
            listed = listed ?: old?.listed ?: false,
            favorite = old?.favorite ?: false,
            favoritedAt = old?.favoritedAt ?: 0L,
        )
        obj.put(seriesId, recordToJson(rec))
        prune(500)
        sp.edit().putString("progress", obj.toString()).apply()
        rec
    }

    fun markListed(seriesId: String) = synchronized(lock) {
        val obj = progressJson()
        val o = obj.optJSONObject(seriesId) ?: return@synchronized
        val rec = jsonToRecord(o)
        if (rec.listed) return@synchronized
        obj.put(seriesId, recordToJson(rec.copy(listed = true)))
        sp.edit().putString("progress", obj.toString()).apply()
    }

    fun setFavorite(
        seriesId: String,
        favorite: Boolean,
        snapshot: ProgressRecord? = null,
    ) = synchronized(lock) {
        val obj = progressJson()
        val old = obj.optJSONObject(seriesId)?.let { jsonToRecord(it) } ?: snapshot ?: return@synchronized
        val now = System.currentTimeMillis()
        val rec = old.copy(
            favorite = favorite,
            favoritedAt = if (favorite) now else 0L,
        )
        obj.put(seriesId, recordToJson(rec))
        sp.edit().putString("progress", obj.toString()).apply()
    }

    fun clearHistory() = synchronized(lock) {
        val obj = progressJson()
        for (key in keysOf(obj)) {
            val o = obj.optJSONObject(key) ?: continue
            val rec = jsonToRecord(o)
            obj.put(key, recordToJson(rec.copy(listed = false)))
        }
        sp.edit().putString("progress", obj.toString()).apply()
    }

    fun clearFavorites() = synchronized(lock) {
        val obj = progressJson()
        for (key in keysOf(obj)) {
            val o = obj.optJSONObject(key) ?: continue
            val rec = jsonToRecord(o)
            obj.put(key, recordToJson(rec.copy(favorite = false, favoritedAt = 0L)))
        }
        sp.edit().putString("progress", obj.toString()).apply()
    }

    private fun prune(limit: Int) {
        val obj = progressJson()
        if (obj.length() <= limit) return
        val entries = ArrayList<Pair<String, ProgressRecord>>(obj.length())
        for (key in keysOf(obj)) {
            val rec = obj.optJSONObject(key)?.let { jsonToRecord(it) } ?: continue
            entries.add(key to rec)
        }
        entries.sortWith(compareByDescending<Pair<String, ProgressRecord>> { it.second.listed || it.second.favorite }
            .thenByDescending { it.second.updatedAt })
        val keep = entries.take(limit)
        val fresh = JSONObject()
        for ((key, rec) in keep) fresh.put(key, recordToJson(rec))
        sp.edit().putString("progress", fresh.toString()).apply()
    }

    // ---------- 搜索记录 ----------

    fun searches(): List<String> = synchronized(lock) {
        val raw = sp.getString("searches", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addSearch(word: String) = synchronized(lock) {
        val q = word.trim()
        if (q.isEmpty()) return@synchronized
        val list = (listOf(q) + searches().filter { it != q }).take(10)
        sp.edit().putString("searches", JSONArray(list).toString()).apply()
    }

    fun clearSearches() {
        synchronized(lock) { sp.edit().putString("searches", "[]").apply() }
    }

    // ---------- 完全重置 ----------

    fun resetAll() = synchronized(lock) {
        sp.edit().clear().apply()
        _prefs.value = Prefs()
    }

    // ---------- 信息流缓存 ----------

    fun loadFeed(channel: String, dir: java.io.File): FeedCache? {
        val file = java.io.File(dir, "feed_${channel.hashCode().toUInt().toString(16)}.json")
        if (!file.exists()) return null
        return try {
            val o = JSONObject(file.readText())
            val arr = o.optJSONArray("items") ?: JSONArray()
            val items = buildList {
                for (i in 0 until arr.length()) {
                    val d = arr.optJSONObject(i) ?: continue
                    add(
                        Drama(
                            id = d.optString("id"),
                            title = d.optString("title"),
                            description = d.optString("desc"),
                            coverUrl = d.optString("cover"),
                            episodeCount = d.optInt("count"),
                        )
                    )
                }
            }
            if (items.isEmpty()) null else FeedCache(
                items = items,
                cursor = o.optString("cursor"),
                hasMore = o.optBoolean("has_more"),
                savedAt = o.optLong("saved_at"),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveFeed(channel: String, dir: java.io.File, page: FeedPage) {
        try {
            dir.mkdirs()
            val file = java.io.File(dir, "feed_${channel.hashCode().toUInt().toString(16)}.json")
            val arr = JSONArray()
            for (d in page.items) {
                arr.put(JSONObject().apply {
                    put("id", d.id)
                    put("title", d.title)
                    put("desc", d.description)
                    put("cover", d.coverUrl)
                    put("count", d.episodeCount)
                })
            }
            val o = JSONObject().apply {
                put("items", arr)
                put("cursor", page.cursor)
                put("has_more", page.hasMore)
                put("saved_at", System.currentTimeMillis())
            }
            file.writeText(o.toString())
        } catch (_: Exception) {
            // 缓存的失败不影响播放
        }
    }

    fun clearFeeds(dir: java.io.File) {
        try {
            dir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }
}