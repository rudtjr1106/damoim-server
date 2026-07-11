package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.UserOAuthAccount
import com.damoim.server.domain.enums.OAuthProvider
import org.springframework.data.jpa.repository.JpaRepository

interface UserOAuthAccountRepository : JpaRepository<UserOAuthAccount, Long> {
    fun findByProviderAndProviderUserId(provider: OAuthProvider, providerUserId: String): UserOAuthAccount?
}
