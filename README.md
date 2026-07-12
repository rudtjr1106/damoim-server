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

## API 문서 (Swagger UI / OpenAPI)

서버 실행 후:

- **Swagger UI**: <http://localhost:8080/swagger-ui.html> — 브라우저에서 전 엔드포인트(A~G) 탐색·시험 호출
- **OpenAPI 스펙(JSON)**: <http://localhost:8080/v3/api-docs>

인증이 필요한 요청은 우측 상단 **Authorize**에 JWT 액세스 토큰(`POST /api/auth/kakao` 응답)을 넣으면 자동 적용된다.
모든 응답은 공통 봉투 `{ success, data, error }`로 감싸지며, Swagger에 표기된 스키마는 `data` 필드의 내용이다.

> 운영에서 문서 노출을 끄려면 `application-prod.yml`에 `springdoc.api-docs.enabled: false`, `springdoc.swagger-ui.enabled: false`를 추가한다.

## 구현 범위 (A~G)

CMP 앱 화면 그룹 A~G 전체의 서버 엔드포인트 구현 완료(각 그룹 적대적 보안 리뷰 반영):
A 인증(카카오·JWT) · B 동아리·가입 · C 게시판 · D 자료실(S3) · E 회원·기수·프로필·멀티동아리 ·
F 일정·이벤트 · G 설정·구독·권한·차단·알림. 전 엔드포인트는 Swagger UI에서 확인.

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

## 파일 스토리지(자료실 S3 연결)

자료실 파일은 **S3 presigned URL**로 처리(클라가 S3에 직접 업/다운, 서버는 URL·메타만).
`StorageService` 추상화라 **코드 수정 없이 설정만**으로 로컬 스텁 ↔ 실제 S3 전환.

**1. 버킷 생성 + 비공개 유지**(접근은 presigned URL로만):
```bash
aws s3api create-bucket --bucket damoim-files --region ap-northeast-2 \
  --create-bucket-configuration LocationConstraint=ap-northeast-2
# 퍼블릭 액세스 차단(기본 ON 유지 권장)
```

**2. IAM 최소 권한**(서버가 presign 서명·HeadObject·삭제):
```json
{ "Version": "2012-10-17", "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
    "Resource": "arn:aws:s3:::damoim-files/*" }] }
```

**3. 환경변수 주입** → `S3StorageService` 활성화:
```bash
STORAGE_PROVIDER=s3 S3_BUCKET=damoim-files S3_REGION=ap-northeast-2 \
AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=... ./gradlew bootRun
# 운영(EC2/ECS)은 정적 키 대신 IAM 역할 권장 → 키 없이 동작
```

**4. CORS**(웹/WebView 업로드면 필수, 네이티브 앱은 불필요):
```json
[{ "AllowedOrigins": ["*"], "AllowedMethods": ["PUT", "GET"],
   "AllowedHeaders": ["*"], "MaxAgeSeconds": 3000 }]
```

**업로드 흐름(클라 3단계)**: `POST /api/resources/upload-url` → 받은 URL로 파일 **직접 PUT** → `POST /api/resources`(storageKey 포함, 서버가 HeadObject로 실크기 확인 후 등록). 다운로드는 `GET /api/resources/{id}/download-url`.

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
