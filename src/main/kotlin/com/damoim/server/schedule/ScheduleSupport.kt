package com.damoim.server.schedule

import com.damoim.server.domain.entity.Event
import com.damoim.server.domain.entity.EventAnswer
import com.damoim.server.domain.entity.FormQuestion
import com.damoim.server.domain.enums.EventStatus
import com.damoim.server.domain.repository.EventAnswerRepository
import com.damoim.server.domain.repository.FormQuestionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

/**
 * 일정/이벤트 공용 파생·저장 로직(조회 서비스와 신청 서비스가 공유).
 *
 * **status 파생 정책(Mock보다 정교)**: 정원참은 저장하지 않고 파생(appliedCount≥capacity→CLOSED)
 * → 취소로 자리가 나면 자동 재개방. 조기마감(events.status=CLOSED)만 영속되어 sticky하게 유지된다.
 */
@Component
class ScheduleSupport(
    private val formQuestionRepository: FormQuestionRepository,
    private val eventAnswerRepository: EventAnswerRepository,
    private val objectMapper: ObjectMapper,
) {
    /** 지난 일정=ENDED, 조기마감/정원참/마감초과=CLOSED, 그 외 OPEN. lastDate=종료일(없으면 시작일). */
    fun effectiveStatus(
        lastDate: LocalDate,
        event: Event,
        appliedCount: Int,
        today: LocalDate,
        now: Instant,
    ): EventStatus = when {
        lastDate.isBefore(today) -> EventStatus.ENDED
        event.status == EventStatus.CLOSED -> EventStatus.CLOSED
        event.capacity > 0 && appliedCount >= event.capacity -> EventStatus.CLOSED
        event.deadlineAt.isBefore(now) -> EventStatus.CLOSED
        else -> EventStatus.OPEN
    }

    fun parseOptions(json: String?): List<String> =
        json?.takeIf { it.isNotBlank() }?.let {
            runCatching { objectMapper.readValue<List<String>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    fun writeOptions(options: List<String>): String? =
        options.map { it.trim() }.filter { it.isNotEmpty() }
            .takeIf { it.isNotEmpty() }
            ?.let { objectMapper.writeValueAsString(it) }

    fun toFormQuestionResponse(q: FormQuestion) = FormQuestionResponse(
        id = q.id,
        text = q.text,
        type = q.type.name,
        options = parseOptions(q.options),
        required = q.required,
    )

    /** 신청 여러 건의 응답을 배치 복원 — 문항 텍스트를 키로. (리더 신청자 상세·내 신청 공용) */
    fun answersByApplication(appIds: Collection<Long>): Map<Long, List<QuestionAnswerDto>> {
        if (appIds.isEmpty()) return emptyMap()
        return eventAnswerRepository.findAnswerRows(appIds)
            .groupBy { it[0] as Long }
            .mapValues { (_, rows) -> rows.map { QuestionAnswerDto(it[1] as String, it[2] as String) } }
    }

    /**
     * 신청 1건의 응답 저장(신규/수정 공용). 기존 응답 전량 교체(재신청/수정 멱등).
     * 들어온 응답은 **문항 텍스트로 이벤트 폼 문항에 매칭**(클라 계약: QuestionAnswer.question=질문 텍스트).
     * 매칭 안 되는 응답은 무시(스키마 무결성 보호).
     */
    fun replaceAnswers(applicationId: Long, eventId: Long, answers: List<QuestionAnswerDto>) {
        eventAnswerRepository.deleteByApplicationId(applicationId)
        if (answers.isEmpty()) return
        val questionIdByText = formQuestionRepository.findByEventIdOrderByPositionAsc(eventId)
            .associate { it.text to it.id }
        answers.forEach { a ->
            val qid = questionIdByText[a.question] ?: return@forEach
            eventAnswerRepository.save(
                EventAnswer().apply {
                    this.applicationId = applicationId
                    this.questionId = qid
                    this.answer = a.answer
                },
            )
        }
    }
}
