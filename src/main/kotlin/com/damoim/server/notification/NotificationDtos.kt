package com.damoim.server.notification

import com.damoim.server.common.TimeLabels
import com.damoim.server.domain.entity.Notification

/** 알림(37). type으로 아이콘 선택, isUnread면 강조. timeAgo는 created_at 파생.
 *  targetType/targetId는 터치 시 이동 대상(둘 다 null이면 이동 없음). */
data class NotificationResponse(
    val id: Long,
    val type: String,
    val text: String,
    val timeAgo: String,
    val isUnread: Boolean,
    val targetType: String?,
    val targetId: Long?,
) {
    companion object {
        fun from(n: Notification) = NotificationResponse(
            id = n.id,
            type = n.type.name,
            text = n.text,
            timeAgo = n.createdAt?.let { TimeLabels.ago(it) } ?: "",
            isUnread = !n.isRead,
            // 대상은 짝으로만 유효 — 반쪽이면 이동 없음으로 강등(방어)
            targetType = n.targetType?.name?.takeIf { n.targetId != null },
            targetId = n.targetId?.takeIf { n.targetType != null },
        )
    }
}
