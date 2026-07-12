package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.PostDraft
import org.springframework.data.jpa.repository.JpaRepository

interface PostDraftRepository : JpaRepository<PostDraft, Long> {
    fun findByUserId(userId: Long): PostDraft?
    fun deleteByUserId(userId: Long)
}
