package com.damoim.server.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** 스케줄 배치(orphan 스윕)는 켜졌을 때만 활성화 — 불필요한 스케줄러 기동 방지. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = ["app.storage.orphan-sweep.enabled"], havingValue = "true")
class SchedulingConfig
