# ── 빌드 스테이지 ──
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
# 의존성 캐시 레이어: 빌드 스크립트 먼저 복사
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true
# 소스 복사 후 실행 가능한 jar 빌드(테스트는 CI에서 별도 수행)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ── 실행 스테이지 ──
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
# 프로파일·접속정보는 실행 시 환경변수로 주입 (예: -e SPRING_PROFILES_ACTIVE=prod -e DB_URL=...)
ENTRYPOINT ["java", "-jar", "app.jar"]
