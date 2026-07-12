package com.damoim.server.club

import java.security.SecureRandom

/** 가입 코드 생성. 혼동 문자(0/O/1/I) 제외한 대문자+숫자 6자리. */
object JoinCodes {
    private val random = SecureRandom()
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val LENGTH = 6

    fun generate(): String = buildString(LENGTH) {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
}
