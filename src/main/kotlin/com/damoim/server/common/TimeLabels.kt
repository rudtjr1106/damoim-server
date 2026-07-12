package com.damoim.server.common

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * 표시용 상대시간/날짜 라벨을 서버(KST)에서 파생한다. 원천은 timestamptz(created_at 등).
 * 클라 도메인이 라벨 문자열을 쓰므로 서버가 계산해 내려준다(파생값 비저장 원칙).
 */
object TimeLabels {
    private val KST = ZoneId.of("Asia/Seoul")

    fun ago(t: Instant, now: Instant = Instant.now()): String {
        val sec = Duration.between(t, now).seconds.coerceAtLeast(0)
        return when {
            sec < 60 -> "방금 전"
            sec < 3600 -> "${sec / 60}분 전"
            sec < 86_400 -> "${sec / 3600}시간 전"
            sec < 604_800 -> "${sec / 86_400}일 전"
            else -> monthDay(t)
        }
    }

    /** "6.03" */
    fun monthDay(t: Instant): String {
        val d = t.atZone(KST).toLocalDate()
        return "${d.monthValue}.${"%02d".format(d.dayOfMonth)}"
    }

    /** "2026.06.18" */
    fun date(t: Instant): String {
        val d = t.atZone(KST).toLocalDate()
        return "${d.year}.${"%02d".format(d.monthValue)}.${"%02d".format(d.dayOfMonth)}"
    }
}
