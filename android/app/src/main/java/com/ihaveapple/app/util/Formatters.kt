package com.ihaveapple.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatClock(sec: Double): String {
    val total = sec.coerceAtLeast(0.0).toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

fun formatPercent(value: Double): String = "${(value * 100).toInt()}%"

fun formatRelativeTime(ms: Long): String {
    if (ms <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = (now - ms).coerceAtLeast(0)
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "刚刚"
        hours < 1 -> "${minutes} 分钟前"
        days < 1 -> "${hours} 小时前"
        days < 7 -> "${days} 天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ms))
    }
}