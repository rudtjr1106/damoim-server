package com.damoim.server.settings

import jakarta.validation.constraints.Size

// ── 차단(83) ──

data class BlockedUserResponse(
    val id: Long,
    val name: String,
    val initials: String,
    val blockedLabel: String,         // "2026.06.18 차단"
    val isWithdrawn: Boolean,
    val imageUrl: String? = null,     // 내부 업로드 presigned view 또는 외부(카카오) URL
)

// ── 알림 설정(65) ──

/** reminderLabel·dndRangeLabel은 구조화 값(일/시간·시각)에서 서버 파생. */
data class NotifSettingsResponse(
    val push: Boolean,
    val newPost: Boolean,
    val comment: Boolean,
    val scheduleReminder: Boolean,
    val reminderLabel: String,        // "1일 전 · 1시간 전"
    val joinRequest: Boolean,         // 운영(리더 전용 노출은 앱단)
    val eventApply: Boolean,
    val dndEnabled: Boolean,
    val dndRangeLabel: String,        // "23:00 ~ 08:00"
)

/** 65 전체 저장(낙관적 PUT). 라벨은 프리셋 문자열 → 서버가 구조화 값으로 파싱. */
data class UpdateNotifSettingsRequest(
    val push: Boolean = true,
    val newPost: Boolean = true,
    val comment: Boolean = true,
    val scheduleReminder: Boolean = true,
    @field:Size(max = 30) val reminderLabel: String = "",
    val joinRequest: Boolean = true,
    val eventApply: Boolean = true,
    val dndEnabled: Boolean = false,
    @field:Size(max = 30) val dndRangeLabel: String = "",
)
