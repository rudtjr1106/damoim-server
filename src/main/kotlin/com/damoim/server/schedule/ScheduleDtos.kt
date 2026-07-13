package com.damoim.server.schedule

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// ══════════ 응답 ══════════

/**
 * 일정/이벤트(21/22/24). 구조화 값(date/startHour/…/deadlineDate)과 표시 문자열(timeLabel/dday/…)을
 * 함께 내려준다 — 목록/상세는 문자열을, 수정 프리필(23)은 구조화 값을 사용한다.
 */
data class ScheduleResponse(
    val id: Long,
    val type: String,                 // SCHEDULE / EVENT
    val title: String,
    val date: String,                 // ISO LocalDate "2026-07-22"
    val timeLabel: String,            // "오전 10:00"
    val startHour: Int,
    val startMinute: Int,
    val endLabel: String?,            // "~7.25 (금)" / null
    val endDate: String?,             // ISO / null
    val endHour: Int,
    val endMinute: Int,
    val location: String,
    val memo: String,
    val accent: String,               // PRIMARY / SKY
    val addedToMyCalendar: Boolean,   // 요청자별
    val hostName: String,
    val createdAt: Long,              // 동일 날짜 내 정렬 보조(epoch millis)
    val event: EventResponse?,        // SCHEDULE이면 null
)

data class EventResponse(
    val capacity: Int,
    val appliedCount: Int,
    val deadlineDate: String,         // ISO LocalDate
    val deadlineLabel: String,        // "7.22 (목) 오후 6:00"
    val status: String,               // OPEN / CLOSED / ENDED (파생)
    val dday: String,                 // "D-10" / "D-DAY" / "종료" (시작일 기준)
    val meta: String,
    val form: List<FormQuestionResponse>,
    val applicants: List<ApplicantResponse>,   // 리더: 전체+응답 / 일반: APPLIED만·응답 없음
    val appliedByMe: Boolean,
    val isMine: Boolean,                        // 모집장(작성자=host) 여부 — true면 신청 대신 신청자 목록
)

data class FormQuestionResponse(
    val id: Long,
    val text: String,
    val type: String,                 // SELECT / MULTI / TEXT
    val options: List<String>,        // SELECT/MULTI만
    val required: Boolean,
)

/** 이벤트 신청자(47 관리 / 24 아바타). answers는 리더에게만 채워진다(PII). */
data class ApplicantResponse(
    val id: Long,                     // event_application id
    val name: String,
    val initials: String,
    val avatarTint: Int,
    val status: String,               // APPLIED / CANCELED
    val appliedLabel: String,         // "10분 전"
    val answers: List<QuestionAnswerDto>,
)

data class QuestionAnswerDto(val question: String, val answer: String)

/** 48 내 신청 내역. eventId = scheduleId(클라 계약). */
data class MyApplicationResponse(
    val eventId: Long,
    val title: String,
    val dateLabel: String,            // "8월 14일 (목)"
    val status: String,               // APPLIED / ENDED
    val answers: List<QuestionAnswerDto>,
)

// ══════════ 요청 ══════════

/** 23 등록/수정. ScheduleDraft 대응. capacity는 정수(빈값이면 null→0). */
data class SaveScheduleRequest(
    @field:NotBlank(message = "제목은 필수입니다.")
    @field:Size(max = 200, message = "제목은 200자 이하여야 합니다.")
    val title: String,
    val startDate: String,            // ISO LocalDate (필수)
    @field:Min(0) @field:Max(23) val startHour: Int = 14,
    @field:Min(0) @field:Max(59) val startMinute: Int = 0,
    val hasEnd: Boolean = false,
    val endDate: String? = null,
    @field:Min(0) @field:Max(23) val endHour: Int = 16,
    @field:Min(0) @field:Max(59) val endMinute: Int = 0,
    @field:Size(max = 200, message = "장소는 200자 이하여야 합니다.")
    val location: String = "",
    @field:Size(max = 5000, message = "메모는 5000자 이하여야 합니다.")
    val memo: String = "",
    val isEvent: Boolean = false,
    @field:Min(0) @field:Max(100000) val capacity: Int? = null,
    val deadlineDate: String? = null,
    @field:Min(0) @field:Max(23) val deadlineHour: Int = 23,
    @field:Min(0) @field:Max(59) val deadlineMinute: Int = 59,
    @field:Valid
    @field:Size(max = 30, message = "질문은 30개 이하여야 합니다.")
    val form: List<FormQuestionInput> = emptyList(),
)

data class FormQuestionInput(
    @field:NotBlank(message = "질문 내용은 필수입니다.")
    @field:Size(max = 500, message = "질문은 500자 이하여야 합니다.")
    val text: String,
    val type: String,                 // SELECT / MULTI / TEXT
    @field:Size(max = 20, message = "선택지는 20개 이하여야 합니다.")
    val options: List<String> = emptyList(),
    val required: Boolean = true,
)

/** 25 신청 / 48 응답 수정 — 공용. */
data class ApplicationAnswersRequest(
    @field:Valid
    @field:Size(max = 50, message = "응답 항목이 너무 많습니다.")
    val answers: List<QuestionAnswerInput> = emptyList(),
)

data class QuestionAnswerInput(
    @field:Size(max = 500) val question: String,
    @field:Size(max = 2000) val answer: String,
)
