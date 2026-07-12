package com.damoim.server.common

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

    private val WEEKDAYS = arrayOf("월", "화", "수", "목", "금", "토", "일")

    /** "D-7" / "D-day" / "마감" (deadline 기준). */
    fun dday(deadline: Instant, now: Instant = Instant.now()): String {
        val today = now.atZone(KST).toLocalDate()
        val target = deadline.atZone(KST).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, target)
        return when {
            days > 0 -> "D-$days"
            days == 0L -> "D-day"
            else -> "마감"
        }
    }

    /** "6.17 (화) 23:59" */
    fun deadlineLabel(deadline: Instant): String {
        val z = deadline.atZone(KST)
        val dow = WEEKDAYS[z.dayOfWeek.value - 1]
        return "${z.monthValue}.${"%02d".format(z.dayOfMonth)} ($dow) " +
            "${"%02d".format(z.hour)}:${"%02d".format(z.minute)}"
    }
}
