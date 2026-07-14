package com.damoim.server.common

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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

    // ── 일정/이벤트(F) 표시 라벨 — 클라 포맷과 1:1 일치 ──

    private fun weekday(d: LocalDate): String = WEEKDAYS[d.dayOfWeek.value - 1]

    /** "오전 10:00" / "오후 7:00" (12시간제). */
    fun koreanTime(hour: Int, minute: Int): String {
        val ampm = if (hour < 12) "오전" else "오후"
        val h12 = (hour % 12).let { if (it == 0) 12 else it }
        return "$ampm $h12:${"%02d".format(minute)}"
    }

    /** "6.12 (목)" */
    fun shortDate(d: LocalDate): String = "${d.monthValue}.${d.dayOfMonth} (${weekday(d)})"

    /** "8월 14일 (목)" */
    fun midDate(d: LocalDate): String = "${d.monthValue}월 ${d.dayOfMonth}일 (${weekday(d)})"

    /** "6.07 토" (홈 다가오는 일정 카드). */
    fun homeDate(d: LocalDate): String = "${d.monthValue}.${"%02d".format(d.dayOfMonth)} ${weekday(d)}"

    /** "8월 14일 목요일" */
    fun longDate(d: LocalDate): String = "${d.monthValue}월 ${d.dayOfMonth}일 ${weekday(d)}요일"

    /** 오늘(KST) 기준 이벤트 D-day — "D-3" / "D-DAY" / "종료". (일정 시작일 기준) */
    fun ddayFromDate(target: LocalDate, today: LocalDate = todayKst()): String {
        val days = ChronoUnit.DAYS.between(today, target)
        return when {
            days > 0 -> "D-$days"
            days == 0L -> "D-DAY"
            else -> "종료"
        }
    }

    /** deadline_at(Instant)에서 "6.12 (목) 오전 10:00" 파생(KST). */
    fun deadlineDateTime(deadline: Instant): String {
        val z = deadline.atZone(KST)
        return "${shortDate(z.toLocalDate())} ${koreanTime(z.hour, z.minute)}"
    }

    fun todayKst(): LocalDate = Instant.now().atZone(KST).toLocalDate()

    /** Instant → KST 달력 날짜. */
    fun kstDate(t: Instant): LocalDate = t.atZone(KST).toLocalDate()

    /** KST 기준 date+시각 → Instant(마감 시각 저장용). */
    fun kstInstant(date: LocalDate, hour: Int, minute: Int): Instant =
        date.atTime(hour, minute).atZone(KST).toInstant()
}
