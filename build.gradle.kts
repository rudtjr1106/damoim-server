import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"   // Spring 빈을 open으로 (프록시)
    kotlin("plugin.jpa") version "2.1.0"      // JPA 엔티티 no-arg 생성자
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.damoim"
version = "0.0.1-SNAPSHOT"

java {
    // 설치된 JDK(Temurin 17)로 컴파일 — 툴체인 자동 다운로드 회피
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    // ── Spring Boot 스타터 ──
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")   // /actuator/health (AWS 헬스체크)

    // ── API 문서 (OpenAPI 3 + Swagger UI: /swagger-ui.html, /v3/api-docs) ──
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.0")

    // ── 레이트리밋 (인메모리 Bucket4j + Caffeine 바운디드 레지스트리) ──
    implementation("com.bucket4j:bucket4j-core:8.10.1")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // ── Kotlin 지원 ──
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // ── JWT (자체 발급 토큰) ──
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // ── JOSE (Apple StoreKit 2 JWS 서명 트랜잭션 검증: ES256 + x5c) ──
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")

    // ── 파일 스토리지 (S3, presigned URL) ──
    implementation(platform("software.amazon.awssdk:bom:2.47.5"))
    implementation("software.amazon.awssdk:s3")

    // ── DB · 마이그레이션 ──
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")   // Flyway 10+ PostgreSQL 지원
    runtimeOnly("org.postgresql:postgresql")

    // ── 테스트 ──
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// JPA 엔티티(@Entity 등)를 open으로 — Hibernate 프록시용 (kotlin-spring이 커버 못 하는 애노테이션 보강)
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")   // Spring nullability 애노테이션 엄격 적용
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
