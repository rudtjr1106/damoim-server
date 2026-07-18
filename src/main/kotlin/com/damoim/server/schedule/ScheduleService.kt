package com.damoim.server.schedule

import com.damoim.server.club.MembershipService
import com.damoim.server.common.BadRequestException
import com.damoim.server.common.ConflictException
import com.damoim.server.common.NotFoundException
import com.damoim.server.common.TimeLabels
import com.damoim.server.domain.entity.BoardPost
import com.damoim.server.domain.entity.Event
import com.damoim.server.domain.entity.FormQuestion
import com.damoim.server.domain.entity.MyCalendarEntry
import com.damoim.server.domain.entity.Schedule
import com.damoim.server.domain.enums.ApplicantStatus
import com.damoim.server.domain.enums.BoardCategory
import com.damoim.server.domain.enums.EventStatus
import com.damoim.server.domain.enums.MemberRole
import com.damoim.server.domain.enums.NotificationTargetType
import com.damoim.server.domain.enums.NotificationType
import com.damoim.server.domain.enums.QuestionType
import com.damoim.server.domain.enums.ScheduleAccent
import com.damoim.server.domain.enums.PermissionType
import com.damoim.server.domain.enums.ScheduleType
import com.damoim.server.domain.repository.BoardPostRepository
import com.damoim.server.domain.repository.EventApplicationRepository
import com.damoim.server.domain.repository.EventRepository
import com.damoim.server.domain.repository.FormQuestionRepository
import com.damoim.server.domain.repository.MyCalendarEntryRepository
import com.damoim.server.domain.repository.ScheduleRepository
import com.damoim.server.domain.repository.UserRepository
import com.damoim.server.notification.NotifyClubEvent
import com.damoim.server.storage.StorageService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

/**
 * 일정/이벤트 조회·CRUD·운영 액션(F). 조회는 활성 회원, 관리(등록/수정/삭제/조기마감/공지)는
 * SCHEDULE_MANAGE 권한(동아리장 전권 또는 부여된 STAFF). 대상은 활성 동아리 스코프로 재검증(IDOR 차단).
 */
@Service
class ScheduleService(
    private val membership: MembershipService,
    private val scheduleRepository: ScheduleRepository,
    private val eventRepository: EventRepository,
    private val formQuestionRepository: FormQuestionRepository,
    private val eventApplicationRepository: EventApplicationRepository,
    private val myCalendarEntryRepository: MyCalendarEntryRepository,
    private val userRepository: UserRepository,
    private val boardPostRepository: BoardPostRepository,
    private val support: ScheduleSupport,
    private val storageService: StorageService,
    private val events: ApplicationEventPublisher,
) {
    // ── 조회 ──

    /** 21/22 목록·캘린더 — 이벤트 신청수·appliedByMe·내일정 여부는 배치 파생. 폼/신청자는 상세에서만. */
    @Transactional(readOnly = true)
    fun list(userId: Long): List<ScheduleResponse> {
        val clubId = membership.currentMembership(userId).clubId
        val schedules = scheduleRepository.findByClubIdOrderByScheduleDateAscCreatedAtAsc(clubId)
        if (schedules.isEmpty()) return emptyList()
        val scheduleIds = schedules.map { it.id }
        val events = eventRepository.findByScheduleIdIn(scheduleIds).associateBy { it.scheduleId }
        val eventIds = events.values.map { it.id }
        val appliedCounts = if (eventIds.isEmpty()) emptyMap() else countMap(
            eventApplicationRepository.countByEventsAndStatus(eventIds, ApplicantStatus.APPLIED),
        )
        val myApplied = if (eventIds.isEmpty()) emptySet() else
            eventApplicationRepository.findAppliedEventIds(userId, eventIds, ApplicantStatus.APPLIED).toSet()
        val myCalendar = myCalendarEntryRepository.findByUserIdAndScheduleIdIn(userId, scheduleIds)
            .map { it.scheduleId }.toSet()
        val hostNames = resolveHostNames(clubId, schedules)
        val today = TimeLabels.todayKst()
        val now = Instant.now()
        return schedules.map { s ->
            val e = events[s.id]
            buildResponse(
                s, e,
                appliedCount = e?.let { appliedCounts[it.id] ?: 0 } ?: 0,
                appliedByMe = e != null && e.id in myApplied,
                isMine = s.hostUserId == userId,
                addedToMyCalendar = s.id in myCalendar,
                form = emptyList(),
                applicants = emptyList(),
                hostName = s.hostUserId?.let { hostNames[it] } ?: "",
                today = today, now = now,
            )
        }
    }

    /** 24 상세 — 폼·신청자 포함. 신청자 응답(answers)은 동아리장에게만(PII). */
    @Transactional(readOnly = true)
    fun detail(userId: Long, scheduleId: Long): ScheduleResponse {
        val member = membership.currentMembership(userId)
        val clubId = member.clubId
        val s = loadScheduleInClub(scheduleId, clubId)
        val e = eventRepository.findByScheduleId(s.id)
        val today = TimeLabels.todayKst()
        val now = Instant.now()

        var appliedCount = 0
        var appliedByMe = false
        var form: List<FormQuestionResponse> = emptyList()
        var applicants: List<ApplicantResponse> = emptyList()
        if (e != null) {
            appliedCount = eventApplicationRepository.countByEventIdAndStatus(e.id, ApplicantStatus.APPLIED).toInt()
            appliedByMe = eventApplicationRepository.findByEventIdAndUserId(e.id, userId)
                ?.status == ApplicantStatus.APPLIED
            form = formQuestionRepository.findByEventIdOrderByPositionAsc(e.id).map(support::toFormQuestionResponse)
            applicants = loadApplicants(e.id, viewerIsLeader = member.memberRole == MemberRole.LEADER, clubId = member.clubId)
        }
        // 44 호스트도 동아리별 표시 이름 우선.
        val hostName = s.hostUserId?.let { membership.displayNamesFor(member.clubId, listOf(it))[it] } ?: ""
        val isMine = s.hostUserId == userId
        return buildResponse(s, e, appliedCount, appliedByMe, isMine, addedToMyCalendar(userId, s.id), form, applicants, hostName, today, now)
    }

    /** 리더는 취소 포함 전체+응답, 일반 회원은 APPLIED만·응답 마스킹(PII). */
    private fun loadApplicants(eventId: Long, viewerIsLeader: Boolean, clubId: Long): List<ApplicantResponse> {
        val all = eventApplicationRepository.findByEventIdOrderByCreatedAtAsc(eventId)
        val visible = if (viewerIsLeader) all else all.filter { it.status == ApplicantStatus.APPLIED }
        if (visible.isEmpty()) return emptyList()
        val users = userRepository.findAllById(visible.map { it.userId }).associateBy { it.id }
        val names = membership.displayNamesFor(clubId, visible.map { it.userId })   // 44 동아리별 표시 이름
        val answers = if (viewerIsLeader) support.answersByApplication(visible.map { it.id }) else emptyMap()
        return visible.map { a ->
            val user = users[a.userId]
            val name = names[a.userId] ?: "탈퇴한 사용자"
            ApplicantResponse(
                id = a.id,
                name = name,
                initials = initialsOf(name),
                avatarTint = (a.id % 4).toInt(),
                status = a.status.name,
                appliedLabel = a.createdAt?.let { TimeLabels.ago(it) } ?: "",
                answers = answers[a.id] ?: emptyList(),
                imageUrl = user?.let { u -> u.profileImageKey?.let { storageService.presignView(it) } ?: u.profileImageUrl },
            )
        }
    }

    // ── CRUD ──

    /** 23 등록 — LEADER. 이벤트면 events+form_questions 함께 생성. 생성 id 반환. */
    @Transactional
    fun create(userId: Long, req: SaveScheduleRequest): Long {
        val member = membership.requirePermission(userId, PermissionType.SCHEDULE_MANAGE)
        val schedule = scheduleRepository.save(
            Schedule().apply {
                clubId = member.clubId
                applyCommon(this, req)
                hostUserId = userId
                accent = ScheduleAccent.PRIMARY
            },
        )
        if (req.isEvent) persistEvent(schedule, req)
        // 새 일정/이벤트 알림 — 동아리 활성 회원 전체(등록자 본인 제외)
        val kind = if (req.isEvent) "이벤트가" else "일정이"
        events.publishEvent(
            NotifyClubEvent(
                clubId = member.clubId,
                actorUserId = userId,
                type = NotificationType.SCHEDULE,
                targetType = NotificationTargetType.SCHEDULE,
                targetId = schedule.id,
                text = "'${schedule.title.take(TITLE_CUT)}' $kind 등록됐어요 (${TimeLabels.shortDate(schedule.scheduleDate)})",
            ),
        )
        return schedule.id
    }

    /** 23 수정 — LEADER. 신청/정원/상태는 보존, 폼은 전량 교체. 타입 전환도 지원. */
    @Transactional
    fun update(userId: Long, scheduleId: Long, req: SaveScheduleRequest): Long {
        val clubId = membership.requirePermission(userId, PermissionType.SCHEDULE_MANAGE).clubId
        val schedule = loadScheduleInClub(scheduleId, clubId)
        applyCommon(schedule, req)
        scheduleRepository.save(schedule)

        val existing = eventRepository.findByScheduleId(schedule.id)
        if (req.isEvent) {
            if (existing == null) {
                persistEvent(schedule, req)
            } else {
                // 신청자가 있으면 양식 변경 차단(응답 CASCADE 소실 방지). 무변경이면 폼 삭제 자체를 생략.
                val hasApplicants = eventApplicationRepository
                    .countByEventIdAndStatus(existing.id, ApplicantStatus.APPLIED) > 0
                val formChanged = !formUnchanged(existing.id, req.form)
                if (hasApplicants && formChanged) {
                    throw ConflictException("신청자가 있어 신청 양식을 변경할 수 없어요.", "EVENT_HAS_APPLICANTS")
                }
                existing.capacity = req.capacity ?: 0
                existing.deadlineAt = deadlineInstant(req, schedule.scheduleDate)
                existing.meta = schedule.location.ifBlank { "이벤트" }
                eventRepository.save(existing) // status/신청 보존
                if (formChanged) {
                    formQuestionRepository.deleteByEventId(existing.id)
                    saveForm(existing.id, req.form)
                }
            }
        } else {
            // 일반 일정으로 전환 — 신청자가 있으면 차단(신청 이력 소실 방지)
            existing?.let {
                if (eventApplicationRepository.countByEventIdAndStatus(it.id, ApplicantStatus.APPLIED) > 0) {
                    throw ConflictException("신청자가 있어 일반 일정으로 바꿀 수 없어요.", "EVENT_HAS_APPLICANTS")
                }
                eventRepository.delete(it) // 스키마 CASCADE
            }
        }
        return schedule.id
    }

    /** 63 삭제 — LEADER. 이벤트/폼/신청/내일정은 스키마 CASCADE로 함께 삭제. */
    @Transactional
    fun delete(userId: Long, scheduleId: Long) {
        val clubId = membership.requirePermission(userId, PermissionType.SCHEDULE_MANAGE).clubId
        val schedule = loadScheduleInClub(scheduleId, clubId)
        scheduleRepository.delete(schedule)
    }

    // ── 운영 액션 ──

    /** 47/62 신청 조기 마감 — LEADER. 이벤트만. 저장된 CLOSED는 sticky(취소로 재개방되지 않음). */
    @Transactional
    fun closeEarly(userId: Long, scheduleId: Long) {
        val clubId = membership.requirePermission(userId, PermissionType.SCHEDULE_MANAGE).clubId
        val schedule = loadScheduleInClub(scheduleId, clubId)
        val event = eventRepository.findByScheduleId(schedule.id)
            ?: throw BadRequestException("이벤트가 아닙니다.", "NOT_AN_EVENT")
        event.status = EventStatus.CLOSED
        eventRepository.save(event)
    }

    /** G5 공지로 알리기 — LEADER. 게시판 필독 NOTICE 글로 자동 등록. */
    @Transactional
    fun announce(userId: Long, scheduleId: Long) {
        val clubId = membership.requirePermission(userId, PermissionType.SCHEDULE_MANAGE).clubId
        val schedule = loadScheduleInClub(scheduleId, clubId)
        val post = boardPostRepository.save(
            BoardPost().apply {
                this.clubId = clubId
                category = BoardCategory.NOTICE
                title = "[이벤트] ${schedule.title}"
                content = buildString {
                    append(TimeLabels.longDate(schedule.scheduleDate))
                    append(" ")
                    append(TimeLabels.koreanTime(schedule.startHour.toInt(), schedule.startMinute.toInt()))
                    if (schedule.location.isNotBlank()) append(" · ${schedule.location}")
                    if (schedule.memo.isNotBlank()) append("\n\n${schedule.memo}")
                }
                authorId = userId
                isPinned = true
            },
        )
        // 이 경로는 BoardService.create()를 거치지 않고 직접 INSERT하므로 알림 훅을 별도로 건다
        // (두 경로는 상호 배타적 → NOTICE 중복 없음).
        events.publishEvent(
            NotifyClubEvent(
                clubId = clubId,
                actorUserId = userId,
                type = NotificationType.NOTICE,
                targetType = NotificationTargetType.POST,
                targetId = post.id,
                text = "새 공지가 등록됐어요: ${post.title.take(TITLE_CUT)}",
            ),
        )
    }

    /** 21/22 내 일정 추가/제거 토글 — 활성 회원. 새 상태 반환. */
    @Transactional
    fun toggleMyCalendar(userId: Long, scheduleId: Long): Boolean {
        val clubId = membership.currentMembership(userId).clubId
        loadScheduleInClub(scheduleId, clubId) // 내 동아리 일정인지 검증(IDOR)
        return if (myCalendarEntryRepository.existsByUserIdAndScheduleId(userId, scheduleId)) {
            myCalendarEntryRepository.deleteByUserIdAndScheduleId(userId, scheduleId)
            false
        } else {
            myCalendarEntryRepository.save(
                MyCalendarEntry().apply { this.userId = userId; this.scheduleId = scheduleId },
            )
            true
        }
    }

    // ── 내부 ──

    private fun applyCommon(s: Schedule, req: SaveScheduleRequest) {
        s.type = if (req.isEvent) ScheduleType.EVENT else ScheduleType.SCHEDULE
        s.title = req.title.trim()
        s.scheduleDate = parseDate(req.startDate) ?: throw BadRequestException("시작 날짜가 올바르지 않습니다.")
        s.startHour = req.startHour.toShort()
        s.startMinute = req.startMinute.toShort()
        if (req.hasEnd && req.endDate != null) {
            s.endDate = parseDate(req.endDate) ?: throw BadRequestException("종료 날짜가 올바르지 않습니다.")
            s.endHour = req.endHour.toShort()
            s.endMinute = req.endMinute.toShort()
        } else {
            s.endDate = null
            s.endHour = null
            s.endMinute = null
        }
        s.location = req.location.trim()
        s.memo = req.memo.trim()
    }

    private fun persistEvent(schedule: Schedule, req: SaveScheduleRequest) {
        val event = eventRepository.save(
            Event().apply {
                scheduleId = schedule.id
                capacity = req.capacity ?: 0
                deadlineAt = deadlineInstant(req, schedule.scheduleDate)
                status = EventStatus.OPEN
                meta = schedule.location.ifBlank { "이벤트" }
            },
        )
        saveForm(event.id, req.form)
    }

    private fun saveForm(eventId: Long, form: List<FormQuestionInput>) {
        // 응답은 문항 텍스트로 매칭되므로 중복 텍스트는 응답 유실을 유발 → 사전 거부
        val texts = form.map { it.text.trim() }
        if (texts.size != texts.toSet().size) {
            throw BadRequestException("질문 내용이 중복돼요.", "DUPLICATE_QUESTION")
        }
        form.forEachIndexed { i, q ->
            val type = parseQuestionType(q.type)
            if (q.options.any { it.length > 200 }) {
                throw BadRequestException("선택지는 200자 이하여야 합니다.", "INVALID_FORM")
            }
            val options = if (type == QuestionType.TEXT) null else support.writeOptions(q.options)
            if (type != QuestionType.TEXT && support.parseOptions(options).size < 2) {
                throw BadRequestException("선택형 질문은 선택지가 2개 이상이어야 합니다.", "INVALID_FORM")
            }
            formQuestionRepository.save(
                FormQuestion().apply {
                    this.eventId = eventId
                    text = q.text.trim()
                    this.type = type
                    this.options = options
                    required = q.required
                    position = i
                },
            )
        }
    }

    /** 기존 폼과 요청 폼이 구조적으로 동일한지(수정 시 불필요한 삭제/재생성 방지·응답 보존). */
    private fun formUnchanged(eventId: Long, incoming: List<FormQuestionInput>): Boolean {
        val existing = formQuestionRepository.findByEventIdOrderByPositionAsc(eventId)
        if (existing.size != incoming.size) return false
        return existing.zip(incoming).all { (e, i) ->
            e.text == i.text.trim() &&
                e.type.name == i.type &&
                e.required == i.required &&
                support.parseOptions(e.options) == i.options.map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    private fun deadlineInstant(req: SaveScheduleRequest, start: LocalDate): Instant {
        val d = if (req.deadlineDate != null) {
            parseDate(req.deadlineDate) ?: throw BadRequestException("마감 날짜가 올바르지 않습니다.")
        } else {
            start
        }
        return TimeLabels.kstInstant(d, req.deadlineHour, req.deadlineMinute)
    }

    private fun buildResponse(
        s: Schedule,
        e: Event?,
        appliedCount: Int,
        appliedByMe: Boolean,
        isMine: Boolean,
        addedToMyCalendar: Boolean,
        form: List<FormQuestionResponse>,
        applicants: List<ApplicantResponse>,
        hostName: String,
        today: LocalDate,
        now: Instant,
    ): ScheduleResponse {
        val eventResp = e?.let {
            EventResponse(
                capacity = it.capacity,
                appliedCount = appliedCount,
                deadlineDate = TimeLabels.kstDate(it.deadlineAt).toString(),
                deadlineLabel = TimeLabels.deadlineDateTime(it.deadlineAt),
                status = support.effectiveStatus(s.endDate ?: s.scheduleDate, it, appliedCount, today, now).name,
                dday = TimeLabels.ddayFromDate(s.scheduleDate, today),
                meta = it.meta ?: "",
                form = form,
                applicants = applicants,
                appliedByMe = appliedByMe,
                isMine = isMine,
            )
        }
        return ScheduleResponse(
            id = s.id,
            type = s.type.name,
            title = s.title,
            date = s.scheduleDate.toString(),
            timeLabel = TimeLabels.koreanTime(s.startHour.toInt(), s.startMinute.toInt()),
            startHour = s.startHour.toInt(),
            startMinute = s.startMinute.toInt(),
            endLabel = s.endDate?.let { "~${TimeLabels.shortDate(it)}" },
            endDate = s.endDate?.toString(),
            endHour = s.endHour?.toInt() ?: 0,
            endMinute = s.endMinute?.toInt() ?: 0,
            location = s.location,
            memo = s.memo,
            accent = s.accent.name,
            addedToMyCalendar = addedToMyCalendar,
            hostName = hostName,
            createdAt = s.createdAt?.toEpochMilli() ?: 0,
            event = eventResp,
        )
    }

    private fun addedToMyCalendar(userId: Long, scheduleId: Long) =
        myCalendarEntryRepository.existsByUserIdAndScheduleId(userId, scheduleId)

    private fun resolveHostNames(clubId: Long, schedules: List<Schedule>): Map<Long, String> {
        val ids = schedules.mapNotNull { it.hostUserId }.distinct()
        if (ids.isEmpty()) return emptyMap()
        return membership.displayNamesFor(clubId, ids)   // 44 동아리별 표시 이름
    }

    private fun loadScheduleInClub(scheduleId: Long, clubId: Long): Schedule {
        val s = scheduleRepository.findById(scheduleId)
            .orElseThrow { NotFoundException("일정을 찾을 수 없습니다.") }
        if (s.clubId != clubId) throw NotFoundException("일정을 찾을 수 없습니다.")
        return s
    }

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value.trim()) }.getOrNull()

    private fun parseQuestionType(value: String): QuestionType =
        runCatching { QuestionType.valueOf(value) }.getOrElse {
            throw BadRequestException("질문 유형이 올바르지 않습니다.", "INVALID_QUESTION_TYPE")
        }

    private fun initialsOf(name: String) = if (name.length >= 3) name.takeLast(2) else name

    private fun countMap(rows: List<Array<Any>>): Map<Long, Int> =
        rows.associate { (it[0] as Long) to (it[1] as Long).toInt() }

    private companion object {
        const val TITLE_CUT = 60   // 알림 문구에 넣을 제목 최대 길이
    }
}
