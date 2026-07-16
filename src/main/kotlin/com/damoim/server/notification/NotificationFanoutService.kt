package com.damoim.server.notification

import com.damoim.server.domain.entity.Notification
import com.damoim.server.domain.entity.NotificationSettings
import com.damoim.server.domain.enums.MemberStatus
import com.damoim.server.domain.enums.NotificationType
import com.damoim.server.domain.repository.ClubMemberRepository
import com.damoim.server.domain.repository.NotificationRepository
import com.damoim.server.domain.repository.NotificationSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 인앱 알림 팬아웃. 본 기능 트랜잭션이 **커밋된 뒤에만** 실행되고(AFTER_COMMIT),
 * 자체 트랜잭션(REQUIRES_NEW)에서 배치 저장한다. 실패는 로그만 남기고 삼킨다
 * — 알림은 부가 기능이라 글 등록·일정 등록을 롤백시켜선 안 된다.
 * 동기 실행(같은 스레드)이라 별도 스레드풀·보안컨텍스트 전파 문제가 없다.
 */
@Service
class NotificationFanoutService(
    private val notificationRepository: NotificationRepository,
    private val clubMemberRepository: ClubMemberRepository,
    private val notificationSettingsRepository: NotificationSettingsRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handle(event: NotifyEvent) {
        runCatching {
            // 수신자는 **반드시** 해당 동아리의 활성 회원으로 한정한다. 지정 수신자(댓글/답글)도 예외 없다 —
            // 글은 남고 명부 행은 삭제되므로(내보내기·탈퇴) post.authorId가 이미 비회원일 수 있고,
            // 그대로 두면 남의 동아리 댓글 내용이 비회원에게 새어 나간다.
            val active = clubMemberRepository.findUserIdsByClubIdAndStatus(event.clubId, MemberStatus.ACTIVE)
            val recipients = when (event) {
                is NotifyClubEvent -> active.filter { it != event.actorUserId }   // 행위자 본인 제외
                is NotifyUsersEvent -> active.toSet().let { m -> event.recipientIds.filter { it in m } }
            }.distinct()
            if (recipients.isEmpty()) return@runCatching
            if (recipients.size > MAX_FANOUT) {
                // 상한 초과는 잘라내지 않고 경고만 — 조용한 누락이 더 나쁘다(현실적으로 도달 불가)
                log.warn("알림 팬아웃 상한 초과: club={} type={} n={}", event.clubId, event.type, recipients.size)
            }
            // 설정은 1회 배치 조회 → Map(유저당 조회 금지, N+1 방지)
            val settings = notificationSettingsRepository.findByUserIdIn(recipients).associateBy { it.userId }
            val allowed = recipients.filter { allows(settings[it], event.type) }
            allowed.chunked(BATCH).forEach { chunk ->
                notificationRepository.saveAll(chunk.map { uid -> newNotification(uid, event) })
            }
        }.onFailure {
            log.warn(
                "알림 생성 실패(본 기능은 영향 없음): club={} type={} target={}/{}",
                event.clubId, event.type, event.targetType, event.targetId, it,
            )
        }
    }

    private fun newNotification(uid: Long, e: NotifyEvent) = Notification().apply {
        userId = uid
        clubId = e.clubId
        type = e.type
        text = e.text.take(TEXT_MAX)   // text varchar(500)
        isRead = false
        targetType = e.targetType
        targetId = e.targetId
    }

    /**
     * 알림 설정(65) 존중 — 설정 행이 없으면 기본값(전부 켬)이라 수신.
     * 깔끔히 대응되는 항목만 게이팅: comment→COMMENT, newPost→NOTICE·VOTE.
     * SCHEDULE/JOIN_APPROVED는 대응 필드가 없어 게이팅하지 않는다.
     * push는 FCM 마스터 토글이라 인앱 목록 게이팅에 쓰지 않는다(끄면 목록이 비는 회귀).
     */
    private fun allows(s: NotificationSettings?, type: NotificationType): Boolean {
        if (s == null) return true
        return when (type) {
            NotificationType.COMMENT -> s.comment
            NotificationType.NOTICE, NotificationType.VOTE -> s.newPost
            NotificationType.SCHEDULE, NotificationType.JOIN_APPROVED -> true
        }
    }

    private companion object {
        const val BATCH = 500        // saveAll 청크 — 대형 동아리에서 단일 flush 폭주 방지
        const val MAX_FANOUT = 5000  // 경고 임계(차단 아님)
        const val TEXT_MAX = 500
    }
}
