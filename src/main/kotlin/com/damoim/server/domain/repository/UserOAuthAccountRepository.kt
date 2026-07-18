package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.UserOAuthAccount
import com.damoim.server.domain.enums.OAuthProvider
import org.springframework.data.jpa.repository.JpaRepository

interface UserOAuthAccountRepository : JpaRepository<UserOAuthAccount, Long> {
    fun findByProviderAndProviderUserId(provider: OAuthProvider, providerUserId: String): UserOAuthAccount?

    /** 회원 탈퇴 시 소셜 링크 제거 → 같은 계정으로 새로 가입 가능(탈퇴 계정 부활 방지). */
    fun deleteByUserId(userId: Long)
}
