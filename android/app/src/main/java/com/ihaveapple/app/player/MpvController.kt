package com.ihaveapple.app.player

import android.content.Context
import android.view.Surface
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 一层薄薄的 libmpv 封装。UI 只读 StateFlow、只发命令；
 * 加密分集的内容密钥通过 loadfile 的 per-file 选项交给 lavf。
 */
class MpvController(context: Context) {

    private val mpv: MPVLib = MPVLib.create(context) ?: error("libmpv 初始化失败")
    private var initialized = false

    private var pendingSeekSec = 0.0
    private var pendingAutoPlay = true
    private var currentKey: String? = null
    private var eofReported = false
    private var awaitingLoad = false

    private val _timePos = MutableStateFlow(0.0)
    val timePos: StateFlow<Double> = _timePos.asStateFlow()

    private val _duration = MutableStateFlow(0.0)
    val duration: StateFlow<Double> = _duration.asStateFlow()

    private val _paused = MutableStateFlow(true)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()

    private val _videoSize = MutableStateFlow(0 to 0)
    val videoSize: StateFlow<Pair<Int, Int>> = _videoSize.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _ended = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val ended: SharedFlow<Int> = _ended.asSharedFlow()

    private val _failed = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val failed: SharedFlow<String> = _failed.asSharedFlow()

    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String, value: Double) {
            when (property) {
                "time-pos" -> _timePos.value = value
                "duration" -> _duration.value = value
            }
        }

        override fun eventProperty(property: String, value: Boolean) {
            when (property) {
                "pause" -> _paused.value = value
                "paused-for-cache" -> _buffering.value = value
                "eof-reached" -> if (value && !eofReported) {
                    eofReported = true
                    _ended.tryEmit(MPVLib.MpvEvent.MPV_EVENT_END_FILE)
                }
            }
        }

        override fun eventProperty(property: String, value: Long) {
            when (property) {
                "width" -> _videoSize.value = value.toInt() to _videoSize.value.second
                "height" -> _videoSize.value = _videoSize.value.first to value.toInt()
            }
        }

        override fun eventProperty(property: String) = Unit

        override fun eventProperty(property: String, value: String) = Unit

        override fun event(eventId: Int) {
            when (eventId) {
                MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                    awaitingLoad = false
                    _loaded.value = true
                    _buffering.value = false
                    val seek = pendingSeekSec
                    pendingSeekSec = 0.0
                    if (seek > 0.5) {
                        command("seek", seek.toString(), "absolute+exact")
                    }
                    if (pendingAutoPlay) {
                        setPause(false)
                    } else {
                        setPause(true)
                    }
                }

                MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
                    _loaded.value = false
                }

                MPVLib.MpvEvent.MPV_EVENT_IDLE -> {
                    // 载入失败时 mpv 回到 idle；成功载入后 idle 才出现则与本次 load 无关
                    if (awaitingLoad) {
                        awaitingLoad = false
                        _loaded.value = false
                        _failed.tryEmit("无法播放这一集")
                    }
                }
            }
        }
    }

    fun init() {
        if (initialized) return
        applyOptions()
        mpv.init()
        initialized = true
        mpv.addObserver(observer)
        observe("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        observe("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        observe("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        observe("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        observe("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        observe("width", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        observe("height", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        setPause(true)
    }

    private fun applyOptions() {
        fun opt(name: String, value: String) {
            runCatching { mpv.setOptionString(name, value) }
        }
        opt("config", "no")
        opt("idle", "yes")
        opt("vo", "gpu")
        opt("gpu-context", "android")
        opt("hwdec", "auto-safe")
        opt("ao", "audiotrack,opensles")
        opt("audio-client-name", "IHaveApple")
        opt("osc", "no")
        opt("osd-level", "0")
        opt("input-default-bindings", "no")
        opt("input-vo-keyboard", "no")
        opt("keep-open", "yes")
        opt("panscan", "1.0")
        opt("cache", "yes")
        opt("cache-secs", "20")
        opt("demuxer-max-bytes", "64MiB")
        opt("demuxer-readahead-secs", "20")
        opt("tls-verify", "no")
        opt("msg-level", "all=warn")
        opt("volume-max", "100")
        opt("user-agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
    }

    private fun observe(name: String, format: Int) {
        runCatching { mpv.observeProperty(name, format) }
    }

    fun attachSurface(surface: Surface) {
        if (!initialized) return
        mpv.attachSurface(surface)
    }

    fun detachSurface() {
        if (!initialized) return
        mpv.detachSurface()
    }

    /**
     * 载入一集。keyHex 不为空时作为 AES-CTR 内容密钥交给 lavf；
     * startSec 在文件加载完成后跳转。
     */
    fun load(url: String, keyHex: String?, startSec: Double, autoPlay: Boolean = true) {
        if (!initialized) return
        currentKey = keyHex
        pendingSeekSec = startSec.coerceAtLeast(0.0)
        pendingAutoPlay = autoPlay
        eofReported = false
        awaitingLoad = true
        _loaded.value = false
        _timePos.value = startSec.coerceAtLeast(0.0)
        _duration.value = 0.0
        _buffering.value = false
        // mpv 的 loadfile 第 3 个参数是播放列表下标（整数），不能直接传选项；
        // demuxer-lavf-o 是全局选项，每次 load 前覆盖成当前这一集需要的值。
        mpv.setOptionString(
            "demuxer-lavf-o",
            if (keyHex.isNullOrBlank()) "" else "decryption_key=$keyHex",
        )
        mpv.command(arrayOf("loadfile", url, "replace"))
    }

    fun stop() {
        if (!initialized) return
        _loaded.value = false
        _timePos.value = 0.0
        _duration.value = 0.0
        command("stop")
    }

    fun setPause(pause: Boolean) {
        if (!initialized) return
        runCatching { mpv.setPropertyBoolean("pause", pause) }
    }

    fun togglePause() {
        setPause(!_paused.value)
    }

    fun seekTo(sec: Double) {
        if (!initialized) return
        val target = sec.coerceAtLeast(0.0)
        _timePos.value = target
        command("seek", target.toString(), "absolute+exact")
    }

    fun setSpeed(speed: Double) {
        if (!initialized) return
        runCatching { mpv.setPropertyDouble("speed", speed.coerceIn(0.25, 4.0)) }
    }

    fun setVolume(volume: Int) {
        if (!initialized) return
        runCatching { mpv.setPropertyDouble("volume", volume.coerceIn(0, 100).toDouble()) }
    }

    fun setMute(mute: Boolean) {
        if (!initialized) return
        runCatching { mpv.setPropertyBoolean("mute", mute) }
    }

    fun setPanscan(value: Double) {
        if (!initialized) return
        runCatching { mpv.setPropertyDouble("panscan", value) }
    }

    fun retryKey() = currentKey

    private fun command(vararg args: String) {
        runCatching { mpv.command(arrayOf(*args)) }
    }

    fun release() {
        if (!initialized) return
        initialized = false
        runCatching { mpv.removeObserver(observer) }
        runCatching { mpv.detachSurface() }
        runCatching { mpv.destroy() }
    }
}