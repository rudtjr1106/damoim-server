# damoim-server

다모임(동아리 커뮤니티 플랫폼) 백엔드 API 서버.
[Damoim CMP 앱](../Damoim)의 `Mock*Repository`를 대체할 실제 서버 — 경계는 JSON 계약.

- **스택**: Kotlin · Spring Boot 3.4 · Spring Data JPA(Hibernate) · Flyway · Spring Security · PostgreSQL 16
- **Java**: 17 · **Gradle**: 8.14 (wrapper 포함)

## 빠른 시작 (로컬)

```bash
# 1) PostgreSQL 띄우기 (빈 DB)
docker compose up -d

# 2) 서버 실행 — 부팅 시 Flyway가 V1__init.sql을 적용해 37개 테이블 자동 생성
./gradlew bootRun

# 3) 확인
curl http://localhost:8080/api/ping            # {"service":"damoim-server","status":"ok",...}
curl http://localhost:8080/actuator/health      # {"status":"UP"}

# 생성된 테이블 확인
docker exec damoim-postgres psql -U postgres -d damoim -c "\dt"   # 37 rows
```

## 설정 (환경변수 주입 — 코드는 로컬/운영 무수정)

| 변수 | 기본값(로컬) | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `local` / `prod` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/damoim` | JDBC URL |
| `DB_USERNAME` | `postgres` | DB 사용자 |
| `DB_PASSWORD` | `dev` | DB 비밀번호 (운영은 시크릿으로) |
| `SERVER_PORT` | `8080` | 서버 포트 |

`.env.example`를 `.env`로 복사해 사용. **`.env`·시크릿은 커밋 금지**(`.gitignore` 처리됨).

## AWS 등 배포

동일 코드, 설정만 스위칭:
```bash
# Docker 이미지로
docker build -t damoim-server .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=jdbc:postgresql://<RDS엔드포인트>:5432/damoim \
  -e DB_USERNAME=... -e DB_PASSWORD=... \
  damoim-server
```
DB는 AWS **RDS(PostgreSQL)** 사용 → 부팅 시 **같은 `V1__init.sql`이 Flyway로 실행**돼 스키마 이식.

## 구조

```
src/main/kotlin/com/damoim/server/
├── DamoimServerApplication.kt    # 시작점
├── config/SecurityConfig.kt      # 임시 permitAll (TODO: JWT)
└── web/HealthController.kt        # /api/ping
src/main/resources/
├── application.yml               # 공통(환경변수 주입)
├── application-local.yml         # 로컬 프로파일
├── application-prod.yml          # 운영 프로파일
└── db/migration/V1__init.sql     # 37개 테이블 스키마 (Flyway)
```

## 다음 단계 (구현 예정)

1. JPA 엔티티 매핑 (37개 테이블 → `@Entity`)
2. 도메인별 REST 엔드포인트 (auth → club → board → resource → schedule → settings)
3. 카카오 OAuth 서버측 검증 + JWT 인증 (`SecurityConfig` 교체)
4. 파일 스토리지(S3/R2) 연동
5. CMP 앱 `Mock*Repository` → `Ktor*Repository` 교체
