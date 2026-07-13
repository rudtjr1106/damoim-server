package com.damoim.server.domain.repository

import com.damoim.server.domain.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {
    /** 닉네임 부분일치 유저 id(게시판 작성자 검색용). */
    @Query("select u.id from User u where lower(u.nickname) like lower(concat('%', :q, '%')) escape '!'")
    fun findIdsByNicknameContaining(@Param("q") q: String): List<Long>

    /** orphan 스윕 — 참조 중인 전 프로필 사진 키. */
    @Query("select u.profileImageKey from User u where u.profileImageKey is not null")
    fun findAllProfileImageKeys(): List<String>
}
