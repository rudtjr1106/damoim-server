package com.damoim.server.notification

import com.damoim.server.common.TimeLabels
import com.damoim.server.domain.entity.Notification

/** 알림(37). type으로 아이콘 선택, isUnread면 강조. timeAgo는 created_at 파생. */
data class NotificationResponse(
    val id: Long,
    val type: String,
    val text: String,
    val timeAgo: String,
    val isUnread: Boolean,
) {
    companion object {
        fun from(n: Notification) = NotificationResponse(
            id = n.id,
            type = n.type.name,
            text = n.text,
            timeAgo = n.createdAt?.let { TimeLabels.ago(it) } ?: "",
            isUnread = !n.isRead,
        )
    }
}
