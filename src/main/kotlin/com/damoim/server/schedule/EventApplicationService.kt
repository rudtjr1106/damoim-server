package com.damoim.server.schedule

import com.damoim.server.club.MembershipService
import com.damoim.server.common.BadRequestException
import com.damoim.server.common.ConflictException
import com.damoim.server.common.NotFoundException
import com.damoim.server.common.TimeLabels
import com.damoim.server.domain.entity.EventApplication
import com.damoim.server.domain.entity.Schedule
import com.damoim.server.domain.enums.ApplicantStatus
import com.damoim.server.domain.enums.EventStatus
import com.damoim.server.domain.repository.EventApplicationRepository
import com.damoim.server.domain.repository.EventRepository
import com.damoim.server.domain.repository.FormQuestionRepository
import com.damoim.server.domain.repository.ScheduleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 이벤트 참여 신청·응답 수정·취소·내 신청 내역(25/48). 모두 활성 회원 액션.
 * 신청 가부는 [ScheduleSupport.effectiveStatus] 기준(OPEN일 때만) — 정원참/마감/종료 자동 차단.
 */
@Service
class EventApplicationService(
    private val membership: MembershipService,
    private val scheduleRepository: ScheduleRepository,
    private val eventRepository: EventRepository,
    private val formQuestionRepository: FormQuestionRepository,
    private val eventApplicationRepository: EventApplicationRepository,
    private val support: ScheduleSupport,
) {
    /** 25 참여 신청 — 폼 응답과 함께. 정원참/마감/종료/중복은 거부. */
    @Transactional
    fun apply(userId: Long, scheduleId: Long, answers: List<QuestionAnswerDto>) {
        val clubId = membership.currentMembership(userId).clubId
        val schedule = loadScheduleInClub(scheduleId, clubId)
        val event = eventRepository.findByScheduleId(schedule.id)
            ?: throw BadRequestException("이벤트가 아닙니다.", "NOT_AN_EVENT")

        // 이미 신청한 회원에겐 상태와 무관하게 명확히 안내(중복 체크 우선)
        val existing = eventApplicationRepository.findByEventIdAndUserId(event.id, userId)
        if (existing?.status == ApplicantStatus.APPLIED) {
            throw ConflictException("이미 신청했어요.", "ALREADY_APPLIED")
        }
        // 정원 경쟁(TOCTOU) 차단 — 이벤트 행 비관적 락으로 count→검사→insert를 직렬화
        eventRepository.findByIdForUpdate(event.id)
        val appliedCount = eventApplicationRepository
            .countByEventIdAndStatus(event.id, ApplicantStatus.APPLIED).toInt()
        val status = support.effectiveStatus(
            schedule.endDate ?: schedule.scheduleDate, event, appliedCount, TimeLabels.todayKst(), Instant.now(),
        )
        if (status != EventStatus.OPEN) {
            throw ConflictException("신청할 수 없는 이벤트예요.", "EVENT_NOT_OPEN")
        }
        validateRequiredAnswers(event.id, answers)

        val application = if (existing == null) {
            eventApplicationRepository.save(
                EventApplication().apply {
                    this.eventId = event.id
                    this.userId = userId
                    this.status = ApplicantStatus.APPLIED
                },
            )
        } else { // CANCELED 재신청 → 재활성화
            existing.status = ApplicantStatus.APPLIED
            eventApplicationRepository.save(existing)
        }
        support.replaceAnswers(application.id, event.id, answers)
    }

    /** 48 응답 수정 — 재신청 없이 답변만 교체. */
    @Transactional
    fun updateAnswers(userId: Long, scheduleId: Long, answers: List<QuestionAnswerDto>) {
        val clubId = membership.currentMembership(userId).clubId
        val schedule = loadScheduleInClub(scheduleId, clubId)
        val event = eventRepository.findByScheduleId(schedule.id)
            ?: throw BadRequestException("이벤트가 아닙니다.", "NOT_AN_EVENT")
        val application = eventApplicationRepository.findByEventIdAndUserId(event.id, userId)
            ?.takeIf { it.status == ApplicantStatus.APPLIED }
            ?: throw NotFoundException("신청 내역이 없습니다.", "NOT_APPLIED")
        validateRequiredAnswers(event.id, answers)
        support.replaceAnswers(application.id, event.id, answers)
    }

    /** 48 신청 취소 — 소프트(status=CANCELED). 정원참이 파생이라 자리는 자동 재개방. */
    @Transactional
    fun cancel(userId: Long, scheduleId: Long) {
        val clubId = membership.currentMembership(userId).clubId
        val schedule = loadScheduleInClub(scheduleId, clubId)
        val event = eventRepository.findByScheduleId(schedule.id)
            ?: throw BadRequestException("이벤트가 아닙니다.", "NOT_AN_EVENT")
        val application = eventApplicationRepository.findByEventIdAndUserId(event.id, userId)
            ?.takeIf { it.status == ApplicantStatus.APPLIED }
            ?: throw NotFoundException("신청 내역이 없습니다.", "NOT_APPLIED")
        application.status = ApplicantStatus.CANCELED
        eventApplicationRepository.save(application)
    }

    /** 48 내 신청 내역 — 활성 동아리의 APPLIED 신청. 지난 이벤트는 ENDED. */
    @Transactional(readOnly = true)
    fun myApplications(userId: Long): List<MyApplicationResponse> {
        val clubId = membership.currentMembership(userId).clubId
        val apps = eventApplicationRepository.findMyAppliedInClub(userId, clubId, ApplicantStatus.APPLIED)
        if (apps.isEmpty()) return emptyList()
        val events = eventRepository.findAllById(apps.map { it.eventId }).associateBy { it.id }
        val scheduleIds = events.values.map { it.scheduleId }
        val schedules = scheduleRepository.findAllById(scheduleIds).associateBy { it.id }
        val answers = support.answersByApplication(apps.map { it.id })
        val today = TimeLabels.todayKst()
        return apps.mapNotNull { a ->
            val event = events[a.eventId] ?: return@mapNotNull null
            val s = schedules[event.scheduleId] ?: return@mapNotNull null
            MyApplicationResponse(
                eventId = s.id,
                title = s.title,
                dateLabel = TimeLabels.midDate(s.scheduleDate),
                // 다일 이벤트는 종료일 경과 시 ENDED
                status = if ((s.endDate ?: s.scheduleDate).isBefore(today)) "ENDED" else "APPLIED",
                answers = answers[a.id] ?: emptyList(),
            )
        }
    }

    /** 필수 문항 응답 검증 — 각 required 문항에 대응 텍스트의 비어있지 않은 응답이 있어야 함. */
    private fun validateRequiredAnswers(eventId: Long, answers: List<QuestionAnswerDto>) {
        val answered = answers.filter { it.answer.isNotBlank() }.map { it.question }.toSet()
        val missing = formQuestionRepository.findByEventIdOrderByPositionAsc(eventId)
            .any { it.required && it.text !in answered }
        if (missing) throw BadRequestException("필수 항목에 응답해주세요.", "MISSING_REQUIRED")
    }

    private fun loadScheduleInClub(scheduleId: Long, clubId: Long): Schedule {
        val s = scheduleRepository.findById(scheduleId)
            .orElseThrow { NotFoundException("일정을 찾을 수 없습니다.") }
        if (s.clubId != clubId) throw NotFoundException("일정을 찾을 수 없습니다.")
        return s
    }
}
