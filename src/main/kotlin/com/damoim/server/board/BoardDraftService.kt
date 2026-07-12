package com.damoim.server.board

import com.damoim.server.club.MembershipService
import com.damoim.server.domain.entity.PostDraft
import com.damoim.server.domain.enums.BoardCategory
import com.damoim.server.domain.repository.PostDraftRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 작성 임시저장(유저당 1건). 리치 콘텐츠(첨부·투표·모집)는 payload jsonb에 직렬화. */
@Service
class BoardDraftService(
    private val membership: MembershipService,
    private val postDraftRepository: PostDraftRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun save(userId: Long, req: DraftRequest): DraftResponse {
        val clubId = membership.currentMembership(userId).clubId
        val category = req.category
            ?.let { runCatching { BoardCategory.valueOf(it) }.getOrNull() }
            ?: BoardCategory.FREE
        val payloadJson = objectMapper.writeValueAsString(DraftPayload(req.attachments, req.poll, req.recruit))
        val draft = (postDraftRepository.findByUserId(userId) ?: PostDraft().apply { this.userId = userId }).apply {
            this.clubId = clubId
            this.category = category
            title = req.title?.take(200) ?: ""
            content = req.content ?: ""
            pinned = req.pinned
            payload = payloadJson
        }
        return toResponse(postDraftRepository.save(draft))
    }

    @Transactional(readOnly = true)
    fun load(userId: Long): DraftResponse? = postDraftRepository.findByUserId(userId)?.let { toResponse(it) }

    @Transactional
    fun clear(userId: Long) {
        postDraftRepository.deleteByUserId(userId)
    }

    private fun toResponse(d: PostDraft): DraftResponse {
        val payload = d.payload
            ?.let { runCatching { objectMapper.readValue(it, DraftPayload::class.java) }.getOrNull() }
            ?: DraftPayload()
        return DraftResponse(
            category = d.category.name,
            title = d.title,
            content = d.content,
            pinned = d.pinned,
            attachments = payload.attachments,
            poll = payload.poll,
            recruit = payload.recruit,
        )
    }
}
