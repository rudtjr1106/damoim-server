package com.damoim.server.common

/** 바이트 → 표시용 크기 라벨(파생값, size_bytes에서 계산). */
object SizeLabels {
    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    fun of(rawBytes: Long): String {
        val bytes = rawBytes.coerceAtLeast(0)
        return when {
            bytes >= GB -> "%.1fGB".format(bytes / GB)
            bytes >= MB -> "%.1fMB".format(bytes / MB)
            bytes >= KB -> "%.0fKB".format(bytes / KB)
            else -> "${bytes}B"
        }
    }
}
