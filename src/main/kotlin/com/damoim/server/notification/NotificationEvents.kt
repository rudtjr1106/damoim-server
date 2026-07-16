package com.damoim.server.notification

import com.damoim.server.domain.enums.NotificationTargetType
import com.damoim.server.domain.enums.NotificationType

/**
 * 알림 생성 요청 — 발행 시점이 아니라 **본 트랜잭션 커밋 후**에 처리된다(NotificationFanoutService).
 * 이유: ① 알림 생성 실패가 본 기능(글 등록 등)을 롤백시키면 안 됨
 *       ② 커밋 전에 만들면 본 트랜잭션 롤백 시 없는 글을 가리키는 유령 알림이 남음
 */
sealed interface NotifyEvent {
    val clubId: Long
    val type: NotificationType
    val targetType: NotificationTargetType?
    val targetId: Long?
    val text: String
}

/** 동아리 전체 활성 회원에게(행위자 제외). 회원 수만큼 팬아웃되므로 배치 저장. */
data class NotifyClubEvent(
    override val clubId: Long,
    val actorUserId: Long,
    override val type: NotificationType,
    override val targetType: NotificationTargetType?,
    override val targetId: Long?,
    override val text: String,
) : NotifyEvent

/** 지정 수신자에게만(댓글/답글). recipientIds는 호출측이 본인·중복을 이미 제거해 넘긴다. */
data class NotifyUsersEvent(
    override val clubId: Long,
    val recipientIds: List<Long>,
    override val type: NotificationType,
    override val targetType: NotificationTargetType?,
    override val targetId: Long?,
    override val text: String,
) : NotifyEvent
