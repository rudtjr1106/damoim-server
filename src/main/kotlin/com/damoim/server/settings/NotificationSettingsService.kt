package com.damoim.server.settings

import com.damoim.server.common.UnauthorizedException
import com.damoim.server.domain.entity.NotificationSettings
import com.damoim.server.domain.repository.NotificationSettingsRepository
import com.damoim.server.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalTime

/**
 * 알림 설정(65) — 유저별 전역(동아리 무관). 최초 조회 시 기본값으로 생성.
 * reminder/dnd는 구조화(일·시간·시각) 저장, 표시 라벨은 파생. 클라가 보내는 프리셋 라벨은 파싱해 저장.
 */
@Service
class NotificationSettingsService(
    private val repository: NotificationSettingsRepository,
    private val userRepository: UserRepository,
) {
    /** 조회는 부작용 없음 — 행이 없으면 기본값을 반환(최초 저장 시 생성). */
    @Transactional(readOnly = true)
    fun get(userId: Long): NotifSettingsResponse =
        repository.findByUserId(userId)?.let { toResponse(it) } ?: DEFAULT

    @Transactional
    fun update(userId: Long, req: UpdateNotifSettingsRequest): NotifSettingsResponse {
        val s = getOrCreate(userId)
        s.push = req.push
        s.newPost = req.newPost
        s.comment = req.comment
        s.scheduleReminder = req.scheduleReminder
        s.joinRequest = req.joinRequest
        s.eventApply = req.eventApply
        s.dndEnabled = req.dndEnabled
        // 프리셋 라벨 → 구조화(비어있으면 기존 유지)
        if (req.reminderLabel.isNotBlank()) {
            val (d, h) = parseReminder(req.reminderLabel)
            s.reminderDaysBefore = d
            s.reminderHoursBefore = h
        }
        if (req.dndRangeLabel.isNotBlank()) {
            val (start, end) = parseDnd(req.dndRangeLabel)
            s.dndStart = start
            s.dndEnd = end
        }
        return toResponse(repository.save(s))
    }

    private fun getOrCreate(userId: Long): NotificationSettings =
        repository.findByUserId(userId) ?: run {
            if (!userRepository.existsById(userId)) throw UnauthorizedException()
            repository.save(
                NotificationSettings().apply {
                    this.userId = userId
                    reminderDaysBefore = 1
                    reminderHoursBefore = 1
                    dndStart = LocalTime.of(23, 0)
                    dndEnd = LocalTime.of(8, 0)
                },
            )
        }

    private fun toResponse(s: NotificationSettings) = NotifSettingsResponse(
        push = s.push,
        newPost = s.newPost,
        comment = s.comment,
        scheduleReminder = s.scheduleReminder,
        reminderLabel = reminderLabel(s.reminderDaysBefore, s.reminderHoursBefore),
        joinRequest = s.joinRequest,
        eventApply = s.eventApply,
        dndEnabled = s.dndEnabled,
        dndRangeLabel = dndLabel(s.dndStart, s.dndEnd),
    )

    private fun reminderLabel(days: Int?, hours: Int?): String {
        val parts = buildList {
            days?.let { add("${it}일 전") }
            hours?.let { add("${it}시간 전") }
        }
        return if (parts.isEmpty()) "안 함" else parts.joinToString(" · ")
    }

    private fun dndLabel(start: LocalTime?, end: LocalTime?): String =
        if (start != null && end != null) "${fmt(start)} ~ ${fmt(end)}" else "-"

    private fun fmt(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

    private fun parseReminder(label: String): Pair<Int?, Int?> {
        val d = Regex("(\\d+)\\s*일").find(label)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..365 }
        val h = Regex("(\\d+)\\s*시간").find(label)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..23 }
        return d to h
    }

    private companion object {
        val DEFAULT = NotifSettingsResponse(
            push = true, newPost = true, comment = true, scheduleReminder = true,
            reminderLabel = "1일 전 · 1시간 전",
            joinRequest = true, eventApply = true, dndEnabled = false,
            dndRangeLabel = "23:00 ~ 08:00",
        )
    }

    private fun parseDnd(label: String): Pair<LocalTime?, LocalTime?> {
        val m = Regex("(\\d{1,2}):(\\d{2})\\s*~\\s*(\\d{1,2}):(\\d{2})").find(label)
            ?: return null to null
        return runCatching {
            LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) to
                LocalTime.of(m.groupValues[3].toInt(), m.groupValues[4].toInt())
        }.getOrDefault(null to null)
    }
}
