-- ──────────────────────────────────────────────────────────────────────
-- 검수 반영 요약(damoim-db-schema 워크플로우 리뷰):
--   [critical] resources / resource_cohorts 테이블 복구(자료실 D그룹)
--   [major]    누락 FK 커버링 인덱스 5개 추가(섹션 9)
--   [minor]    중복 좌측프리픽스 인덱스 2개 제거(cohorts/schedules)
--   [minor]    clubs.emblem_color 기본값 0xFF2F6DD3=4281298387로 정정
--   [minor]    poll_votes 단일선택 '1인 1표'는 스키마로 표현 불가 →
--              votePoll 트랜잭션에서 (poll_id,user_id) 기존행 삭제 후 삽입으로 보장.
-- 완전성 감사(damoim-schema-audit, 107 메서드·349 필드 전수 대조) 반영:
--   [minor] payment_records.title 스냅샷 컬럼 추가(플랜변경 후 결제이력 오라벨 방지)
--   [minor] post_reports 테이블 추가(신고 82/ReportReason) — 클라 reportPost 배선은 별도 숙제
-- 총 37개 테이블. critical/major 결함 0건.
-- ──────────────────────────────────────────────────────────────────────

-- =====================================================================
-- Damoim 통합 스키마  (Flyway V1__init.sql)
-- PostgreSQL 15+ / Spring Boot 3 + Kotlin + JPA(Hibernate) + Flyway
-- 규칙: snake_case / 복수형 테이블 / PK bigint GENERATED ALWAYS AS IDENTITY
--       enum = varchar + CHECK / 시각 = timestamptz / 날짜 = date
--       파생·표시용 값은 컬럼화하지 않음(created_at/size_bytes 등에서 파생)
-- 생성 순서는 참조 무결성(FK) 위상정렬을 따른다.
-- =====================================================================

-- =====================================================================
-- 1) Identity & Auth
-- =====================================================================
CREATE TABLE users (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nickname             varchar(50)  NOT NULL,
    email                varchar(255),                          -- 카카오 email scope 거부 시 NULL. 로그인 키 아님 → non-unique
    profile_image_url    text,
    contact              varchar(30),                           -- 프로필 설정(31) 전화번호. 완료 전 NULL
    active_club_id       bigint,                                -- 세션 활성 동아리. FK는 clubs 생성 후 ALTER로 부여
    profile_completed_at timestamptz,                           -- NULL = needsProfileSetup(파생 상태)
    status               varchar(20)  NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE','SUSPENDED','WITHDRAWN')),
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  users IS '로컬 사용자 계정. 카카오 프로필로 최초 생성, 프로필 설정(31)에서 완성. 거의 모든 컨텍스트가 user_id/author_id로 참조.';
COMMENT ON COLUMN users.profile_completed_at IS 'updateProfile 최초 성공 시각. NULL이면 needsProfileSetup=true로 파생. boolean 대신 timestamp로 드리프트 방지+감사.';
COMMENT ON COLUMN users.active_club_id IS 'switchClub/enterClub/observeRole의 세션 활성 동아리. 유저 소유 세션 상태(멤버십 컨텍스트 아님).';
CREATE INDEX ix_users_created_at ON users (created_at);
CREATE INDEX ix_users_email_lower ON users (lower(email)) WHERE email IS NOT NULL;

-- =====================================================================
-- 2) Club · Cohort  (users 이후, 나머지 대부분이 이 둘을 참조)
-- =====================================================================
CREATE TABLE clubs (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name             varchar(100) NOT NULL,
    category         varchar(50)  NOT NULL,
    description      text         NOT NULL DEFAULT '',
    join_code        varchar(20),                               -- 현재 가입 코드. 비활성/미발급 시 NULL
    join_code_active boolean      NOT NULL DEFAULT true,        -- disableJoinCode()가 false로 전환
    emblem_color     bigint       NOT NULL DEFAULT 4281298387,  -- 0xFF2F6DD3 ARGB Long = 4281298387 (검수 픽스). unsigned 32bit라 bigint
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE clubs IS '동아리 엔티티. 가입 코드로 조회, 홈/설정(08)에서 표시.';
-- 활성 코드만 유일성 보장(가입 코드 lookup)
CREATE UNIQUE INDEX ux_clubs_join_code_active ON clubs (join_code)
    WHERE join_code_active AND join_code IS NOT NULL;
CREATE INDEX ix_clubs_category ON clubs (category);

-- users.active_club_id → clubs.id (순환 참조라 clubs 생성 후 FK 부여)
ALTER TABLE users
    ADD CONSTRAINT fk_users_active_club
    FOREIGN KEY (active_club_id) REFERENCES clubs (id) ON DELETE SET NULL;

CREATE TABLE cohorts (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    club_id    bigint      NOT NULL REFERENCES clubs (id) ON DELETE CASCADE,
    label      varchar(80) NOT NULL,                            -- '2024학년 1기 (24기)' 정식 표기
    short      varchar(30) NOT NULL,                            -- '24기' 약칭
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE cohorts IS '동아리 기수(19 관리·42 변경·44 신규). memberCount는 club_members COUNT로 파생.';
CREATE INDEX ix_cohorts_club_created ON cohorts (club_id, created_at);
CREATE UNIQUE INDEX ux_cohorts_club_short ON cohorts (club_id, short); -- 동아리 내 약칭 중복 방지

-- =====================================================================
-- 3) Auth 하위(OAuth · 세션 토큰) — users 참조
-- =====================================================================
CREATE TABLE user_oauth_accounts (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         varchar(20) NOT NULL CHECK (provider IN ('KAKAO')), -- 추후 APPLE/GOOGLE 확장
    provider_user_id varchar(64) NOT NULL,                      -- 카카오 user id. 안정적 외부 로그인 키
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE user_oauth_accounts IS '로컬 계정에 연결된 OAuth 신원. 서버측 카카오 검증이 (provider,provider_user_id)로 유저 조회/생성.';
CREATE INDEX ix_user_oauth_accounts_user ON user_oauth_accounts (user_id);
CREATE UNIQUE INDEX ux_user_oauth_provider_uid ON user_oauth_accounts (provider, provider_user_id);

CREATE TABLE refresh_tokens (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(255) NOT NULL,                           -- 원문 저장 금지, SHA-256 해시
    expires_at timestamptz  NOT NULL,
    revoked_at timestamptz,                                     -- 로그아웃/회전 시 set. non-null=무효
    created_at timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE refresh_tokens IS 'JWT 리프레시 토큰 영속(회전·폐기). access JWT는 stateless.';
CREATE UNIQUE INDEX ux_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);

-- =====================================================================
-- 4) club_members — user↔club 다대다 junction(스키마의 심장)
-- =====================================================================
CREATE TABLE club_members (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    club_id     bigint      NOT NULL REFERENCES clubs (id)   ON DELETE CASCADE,
    user_id     bigint      NOT NULL REFERENCES users (id)   ON DELETE CASCADE,
    member_role varchar(10) NOT NULL DEFAULT 'MEMBER'
                    CHECK (member_role IN ('LEADER','STAFF','MEMBER')), -- 명부등급(단일 진실원). ClubRole은 여기서 파생
    status      varchar(10) NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','DORMANT')),
    cohort_id   bigint      REFERENCES cohorts (id) ON DELETE SET NULL, -- 기수 미배정 허용
    joined_at   timestamptz NOT NULL,                          -- 가입 승인 시각(joinedLabel 파생)
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE  club_members IS 'user↔club 멤버십. 명부등급/상태/기수/가입시각. name/email은 users에서 조인(중복 저장 금지).';
COMMENT ON COLUMN club_members.member_role IS 'MemberRole(LEADER/STAFF/MEMBER). ClubRole(세션역할 LEADER/MEMBER)은 LEADER면 LEADER, 그 외 MEMBER로 파생.';
CREATE UNIQUE INDEX ux_club_members_club_user ON club_members (club_id, user_id); -- 동아리당 유저 1멤버십
CREATE INDEX ix_club_members_user ON club_members (user_id);
CREATE INDEX ix_club_members_club_role ON club_members (club_id, member_role);
CREATE INDEX ix_club_members_cohort ON club_members (cohort_id);
CREATE INDEX ix_club_members_club_joined ON club_members (club_id, joined_at);
-- 동아리장 1명 강제가 필요하면(권한 이양) 아래 부분 유니크를 활성화(현재 미강제):
-- CREATE UNIQUE INDEX ux_club_members_single_leader ON club_members (club_id) WHERE member_role='LEADER';

-- =====================================================================
-- 5) join_applications — 가입 신청 워크플로우
--    (context2 join_requests + context3 join_applications 병합 = 단일 테이블)
-- =====================================================================
CREATE TABLE join_applications (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    club_id           bigint      NOT NULL REFERENCES clubs (id)   ON DELETE CASCADE,
    user_id           bigint      NOT NULL REFERENCES users (id)   ON DELETE CASCADE,
    desired_cohort_id bigint      REFERENCES cohorts (id) ON DELETE SET NULL, -- 희망 기수(미지정 가능)
    message           text,                                      -- 신청 메시지
    status            varchar(16) NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    rejection_reason  text,                                      -- REJECTED일 때만
    decided_at        timestamptz,                               -- 처리 시각. PENDING이면 NULL
    decided_by        bigint      REFERENCES users (id) ON DELETE SET NULL, -- 처리한 동아리장
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_join_app_reject_reason
        CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);
COMMENT ON TABLE join_applications IS '가입 신청(03 코드제출→09 승인/거절). status 단일 컬럼으로 대기/처리완료 구분. 승인 시 club_members 행 생성. context2 join_requests와 context3 join_applications를 하나로 병합.';
CREATE INDEX ix_join_apps_club_status_created ON join_applications (club_id, status, created_at DESC);
CREATE INDEX ix_join_apps_user_created ON join_applications (user_id, created_at DESC);
CREATE INDEX ix_join_apps_desired_cohort ON join_applications (desired_cohort_id);
-- 동일 동아리 중복 대기 신청 방지(거절 후 재신청은 허용)
CREATE UNIQUE INDEX ux_join_apps_pending ON join_applications (club_id, user_id) WHERE status = 'PENDING';

-- =====================================================================
-- 6) Board 컨텍스트
-- =====================================================================
CREATE TABLE board_posts (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    club_id    bigint       NOT NULL REFERENCES clubs (id) ON DELETE CASCADE,
    category   varchar(16)  NOT NULL CHECK (category IN ('NOTICE','FREE','RECRUIT')),
    title      varchar(200) NOT NULL,
    content    text         NOT NULL,                            -- preview는 파생
    author_id  bigint       REFERENCES users (id) ON DELETE SET NULL, -- 탈퇴해도 글 보존
    is_pinned  boolean      NOT NULL DEFAULT false,
    view_count integer      NOT NULL DEFAULT 0,                  -- 열람자 미보관 → 저장 카운터
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    deleted_at timestamptz                                       -- 소프트delete(좋아요/댓글 참조 보존)
);
COMMENT ON TABLE board_posts IS '게시글 본체(공지/자유/모집). likeCount/commentCount/readRate는 junction COUNT로 파생, view_count만 저장 카운터.';
CREATE INDEX ix_board_posts_club_created ON board_posts (club_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_board_posts_club_category ON board_posts (club_id, category, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_board_posts_pinned ON board_posts (club_id, created_at DESC) WHERE is_pinned = true AND deleted_at IS NULL;
CREATE INDEX ix_board_posts_author ON board_posts (author_id);

CREATE TABLE post_attachments (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id     bigint       NOT NULL REFERENCES board_posts (id) ON DELETE CASCADE,
    type        varchar(16)  NOT NULL CHECK (type IN ('IMAGE','FILE_DOC','LINK')),
    image_label varchar(200),                                    -- IMAGE 전용(서버화 시 storage_url)
    file_name   varchar(255),                                    -- FILE_DOC 전용
    size_bytes  bigint,                                          -- FILE_DOC 전용. sizeLabel 파생
    link_title  varchar(300),                                    -- LINK 전용
    link_domain varchar(255),                                    -- LINK 전용
    position    integer      NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_post_attach_type_fields CHECK (
        (type='IMAGE'    AND image_label IS NOT NULL) OR
        (type='FILE_DOC' AND file_name IS NOT NULL AND size_bytes IS NOT NULL) OR
        (type='LINK'     AND link_title IS NOT NULL AND link_domain IS NOT NULL)
    )
);
COMMENT ON TABLE post_attachments IS 'sealed PostAttachment(Image/FileDoc/Link)를 type+nullable컬럼+CHECK로 다형 단일테이블 표현. 항상 게시글과 통째 fetch/replace.';
CREATE INDEX ix_post_attachments_post ON post_attachments (post_id, position);

CREATE TABLE polls (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id      bigint      NOT NULL REFERENCES board_posts (id) ON DELETE CASCADE,
    anonymous    boolean     NOT NULL,
    multi_select boolean     NOT NULL,
    deadline     timestamptz,                                    -- deadlineLabel 파생
    created_at   timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE polls IS '투표(post 1:1). totalVotes/votes는 poll_votes COUNT로 파생.';
CREATE UNIQUE INDEX ux_polls_post ON polls (post_id);

CREATE TABLE poll_options (
    id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    poll_id  bigint       NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    label    varchar(200) NOT NULL,
    position integer      NOT NULL                               -- myVotes의 Int 인덱스와 대응
);
CREATE UNIQUE INDEX ux_poll_options_poll_pos ON poll_options (poll_id, position);
CREATE INDEX ix_poll_options_poll ON poll_options (poll_id, position);

CREATE TABLE poll_votes (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    poll_id        bigint      NOT NULL REFERENCES polls (id)        ON DELETE CASCADE,
    poll_option_id bigint      NOT NULL REFERENCES poll_options (id) ON DELETE CASCADE,
    user_id        bigint      NOT NULL REFERENCES users (id)        ON DELETE CASCADE,
    created_at     timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE poll_votes IS 'user별 투표 junction(votePoll/clearPollVote). 익명이라도 회수·중복방지 위해 user_id 필수. 하드delete로 토글.';
CREATE UNIQUE INDEX ux_poll_votes_option_user ON poll_votes (poll_option_id, user_id);
CREATE INDEX ix_poll_votes_option ON poll_votes (poll_option_id);
CREATE INDEX ix_poll_votes_poll_user ON poll_votes (poll_id, user_id);
CREATE INDEX ix_poll_votes_user ON poll_votes (user_id);

CREATE TABLE post_likes (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    bigint      NOT NULL REFERENCES board_posts (id) ON DELETE CASCADE,
    user_id    bigint      NOT NULL REFERENCES users (id)       ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE post_likes IS '좋아요 junction(toggleLike). likeCount=COUNT, likedByMe=행 존재로 파생.';
CREATE UNIQUE INDEX ux_post_likes_post_user ON post_likes (post_id, user_id);
CREATE INDEX ix_post_likes_post ON post_likes (post_id);
CREATE INDEX ix_post_likes_user ON post_likes (user_id);

CREATE TABLE recruits (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    bigint       NOT NULL REFERENCES board_posts (id) ON DELETE CASCADE,
    status     varchar(16)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    capacity   integer      NOT NULL,
    deadline   timestamptz,
    method     varchar(32),                                      -- '선착순' 등
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE recruits IS '모집(post 1:1). current는 recruit_applications COUNT, remaining/percent/dday 파생.';
CREATE UNIQUE INDEX ux_recruits_post ON recruits (post_id);

CREATE TABLE recruit_applications (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recruit_id bigint      NOT NULL REFERENCES recruits (id) ON DELETE CASCADE,
    user_id    bigint      NOT NULL REFERENCES users (id)    ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now()               -- 선착순 정렬
);
COMMENT ON TABLE recruit_applications IS '모집 신청 junction(applyRecruit). current=COUNT, appliedByMe=행 존재 파생.';
CREATE UNIQUE INDEX ux_recruit_apps_recruit_user ON recruit_applications (recruit_id, user_id);
CREATE INDEX ix_recruit_apps_recruit ON recruit_applications (recruit_id, created_at);
CREATE INDEX ix_recruit_apps_user ON recruit_applications (user_id);

CREATE TABLE comments (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    bigint      NOT NULL REFERENCES board_posts (id) ON DELETE CASCADE,
    author_id  bigint      REFERENCES users (id)    ON DELETE SET NULL, -- 탈퇴해도 댓글 보존
    parent_id  bigint      REFERENCES comments (id) ON DELETE CASCADE,  -- NULL=최상위
    content    text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz                                       -- 소프트delete(답글 트리 보존)
);
COMMENT ON TABLE comments IS '댓글/답글(self-ref). isReply(parent_id)/isAuthor/timeLabel 파생.';
CREATE INDEX ix_comments_post ON comments (post_id, created_at) WHERE deleted_at IS NULL;
CREATE INDEX ix_comments_parent ON comments (parent_id);
CREATE INDEX ix_comments_author ON comments (author_id);

CREATE TABLE post_reads (
    id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id bigint      NOT NULL REFERENCES board_posts (id) ON DELETE CASCADE,
    user_id bigint      NOT NULL REFERENCES users (id)       ON DELETE CASCADE,
    read_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE post_reads IS '공지 수신확인 junction. readRate = COUNT / 동아리원수 * 100(파생).';
CREATE UNIQUE INDEX ux_post_reads_post_user ON post_reads (post_id, user_id);
CREATE INDEX ix_post_reads_post ON post_reads (post_id);

CREATE TABLE post_drafts (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE, -- 유저당 1초안
    club_id    bigint       REFERENCES clubs (id) ON DELETE CASCADE,
    category   varchar(16)  NOT NULL CHECK (category IN ('NOTICE','FREE','RECRUIT')),
    title      varchar(200) NOT NULL DEFAULT '',
    content    text         NOT NULL DEFAULT '',
    pinned     boolean      NOT NULL DEFAULT false,
    payload    jsonb,                                            -- photoLabels/docs/link/Poll/Recruit 중첩 초안(전이 상태)
    updated_at timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE post_drafts IS '작성 임시저장(saveDraft/loadDraft 무인자 단일). 중첩 초안은 통째 저장/교체되는 전이 상태라 jsonb.';
CREATE UNIQUE INDEX ux_post_drafts_user ON post_drafts (user_id);

CREATE TABLE recent_searches (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    query      varchar(100) NOT NULL,                           -- 재검색 시 created_at upsert
    created_at timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE recent_searches IS '유저별 최근 검색어. recommended(추천어)는 서버 트렌딩이라 미저장.';
CREATE UNIQUE INDEX ux_recent_searches_user_query ON recent_searches (user_id, query);
CREATE INDEX ix_recent_searches_user ON recent_searches (user_id, created_at DESC);

CREATE TABLE post_reports (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id     bigint      REFERENCES board_posts (id) ON DELETE CASCADE,
    comment_id  bigint      REFERENCES comments (id)     ON DELETE CASCADE,
    reporter_id bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason      varchar(16) NOT NULL CHECK (reason IN ('SPAM','ABUSE','SEXUAL','FRAUD','PRIVACY','ETC')),
    detail      text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_post_reports_target CHECK ((post_id IS NOT NULL) <> (comment_id IS NOT NULL))  -- 게시글 XOR 댓글
);
COMMENT ON TABLE post_reports IS '게시글/댓글 신고(82, ReportReason 6종). ★감사 픽스: 도메인 enum·화면은 있으나 테이블·BoardRepository.reportPost 부재였음. 실동작하려면 클라에 reportPost 배선 필요.';
CREATE INDEX ix_post_reports_post ON post_reports (post_id);
CREATE INDEX ix_post_reports_comment ON post_reports (comment_id);
CREATE INDEX ix_post_reports_reporter ON post_reports (reporter_id);
-- 중복 신고 방지가 필요하면(유저당 대상당 1회):
-- CREATE UNIQUE INDEX ux_post_reports_post_reporter ON post_reports (post_id, reporter_id) WHERE post_id IS NOT NULL;
-- CREATE UNIQUE INDEX ux_post_reports_comment_reporter ON post_reports (comment_id, reporter_id) WHERE comment_id IS NOT NULL;

-- =====================================================================
-- 7) Schedule & Events
-- =====================================================================
CREATE TABLE schedules (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    club_id      bigint       NOT NULL REFERENCES clubs (id) ON DELETE CASCADE,
    type         varchar(16)  NOT NULL CHECK (type IN ('SCHEDULE','EVENT')),
    title        varchar(200) NOT NULL,
    schedule_date date        NOT NULL,                          -- 캘린더 정렬 기준
    start_hour   smallint     NOT NULL DEFAULT 0 CHECK (start_hour BETWEEN 0 AND 23),
    start_minute smallint     NOT NULL DEFAULT 0 CHECK (start_minute BETWEEN 0 AND 59),
    end_date     date,
    end_hour     smallint     CHECK (end_hour BETWEEN 0 AND 23),
    end_minute   smallint     CHECK (end_minute BETWEEN 0 AND 59),
    location     varchar(200) NOT NULL DEFAULT '',
    memo         text         NOT NULL DEFAULT '',
    accent       varchar(16)  NOT NULL DEFAULT 'PRIMARY' CHECK (accent IN ('PRIMARY','SKY')),
    host_user_id bigint       REFERENCES users (id) ON DELETE SET NULL, -- hostName 파생
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  schedules IS '일정/이벤트 공통. type=EVENT면 events 1:1 존재. 시각은 시/분 구조화 저장(수정 프리필), timeLabel 파생.';
CREATE INDEX ix_schedules_club_date ON schedules (club_id, schedule_date);
CREATE INDEX ix_schedules_host ON schedules (host_user_id);
CREATE INDEX ix_schedules_created ON schedules (created_at);

CREATE TABLE events (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    schedule_id bigint       NOT NULL REFERENCES schedules (id) ON DELETE CASCADE,
    capacity    integer      NOT NULL CHECK (capacity >= 0),
    deadline_at timestamptz  NOT NULL,                           -- dday/deadlineLabel 파생
    status      varchar(16)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED','ENDED')),
    meta        varchar(200),                                    -- '1박 2일 · 가평'
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE events IS 'schedules(type=EVENT)의 1:1 부가정보. EVENT 전용 필드를 분리해 SCHEDULE 행 NULL 방지, 폼/신청 FK 앵커. appliedCount는 event_applications COUNT.';
CREATE UNIQUE INDEX ux_events_schedule ON events (schedule_id);
CREATE INDEX ix_events_status ON events (status);

CREATE TABLE form_questions (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id   bigint      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    text       text        NOT NULL,
    type       varchar(16) NOT NULL CHECK (type IN ('SELECT','MULTI','TEXT')),
    options    jsonb,                                            -- SELECT/MULTI 선택지 배열. TEXT는 NULL
    required   boolean     NOT NULL DEFAULT true,
    position   integer     NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE form_questions IS '이벤트 신청 양식 문항. 순서 있는 단순 선택지 배열이라 options는 jsonb.';
CREATE INDEX ix_form_questions_event ON form_questions (event_id);
CREATE UNIQUE INDEX ux_form_questions_event_pos ON form_questions (event_id, position);

CREATE TABLE event_applications (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id   bigint      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    user_id    bigint      NOT NULL REFERENCES users (id)  ON DELETE CASCADE,
    status     varchar(16) NOT NULL DEFAULT 'APPLIED' CHECK (status IN ('APPLIED','CANCELED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE event_applications IS '이벤트 신청 junction. 유저당 이벤트당 1건. 취소=소프트(status=CANCELED), 재신청=행 재활성화. appliedCount=APPLIED 집계.';
CREATE UNIQUE INDEX ux_event_apps_event_user ON event_applications (event_id, user_id);
CREATE INDEX ix_event_apps_event_status ON event_applications (event_id, status);
CREATE INDEX ix_event_apps_user ON event_applications (user_id);

CREATE TABLE event_answers (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id bigint      NOT NULL REFERENCES event_applications (id) ON DELETE CASCADE,
    question_id    bigint      NOT NULL REFERENCES form_questions (id)     ON DELETE CASCADE,
    answer         text        NOT NULL,                         -- MULTI는 구분자 결합 or jsonb 대안
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE event_answers IS '신청 1건의 문항별 응답. (application_id,question_id) unique로 문항당 1응답 upsert.';
CREATE UNIQUE INDEX ux_event_answers_app_question ON event_answers (application_id, question_id);
CREATE INDEX ix_event_answers_application ON event_answers (application_id);
CREATE INDEX ix_event_answers_question ON event_answers (question_id);

CREATE TABLE my_calendar_entries (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES users (id)     ON DELETE CASCADE,
    schedule_id bigint      NOT NULL REFERENCES schedules (id) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE my_calendar_entries IS '''내 일정에 추가'' junction(toggleMyCalendar). 하드delete로 토글, addedToMyCalendar=행 존재 파생.';
CREATE UNIQUE INDEX ux_my_calendar_user_schedule ON my_calendar_entries (user_id, schedule_id);
CREATE INDEX ix_my_calendar_schedule ON my_calendar_entries (schedule_id);

-- =====================================================================
-- 7b) Resources — 자료실(D그룹 67/68/69)  ★검수 픽스: 통합 누락분 복구
-- =====================================================================
CREATE TABLE resources (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    club_id        bigint       NOT NULL REFERENCES clubs (id) ON DELETE CASCADE,
    folder         varchar(20)  NOT NULL CHECK (folder IN ('DOCS','ACCOUNTING','PRESENTATION','PHOTOS')),
    title          varchar(200) NOT NULL,
    file_name      varchar(255) NOT NULL,
    ext            varchar(16)  NOT NULL,               -- 대문자 확장자(PDF/XLSX) 배지 라벨
    description    text         NOT NULL DEFAULT '',
    size_bytes     bigint       NOT NULL,               -- sizeLabel 파생, 저장공간 바 = SUM 집계
    uploader_id    bigint       REFERENCES users (id) ON DELETE SET NULL, -- 탈퇴해도 자료 보존
    download_count integer      NOT NULL DEFAULT 0,     -- 다운로더 미보관 → 저장 카운터
    visibility     varchar(16)  NOT NULL DEFAULT 'ALL_MEMBERS'
                       CHECK (visibility IN ('ALL_MEMBERS','COHORT_ONLY')),
    page_count     integer,                             -- 68 '문서 미리보기 · 12쪽'(선택)
    storage_url    text,                                -- 서버화 시 오브젝트 스토리지 URL(현재 NULL)
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz                          -- 소프트delete(다운로드 이력 등 참조 보존)
);
COMMENT ON TABLE resources IS '자료실 파일(1급 엔티티). uploaderName/uploaderIsLeader/sizeLabel/uploadedLabel은 조인·파생, 실제 바이너리는 storage_url이 가리키는 오브젝트 스토리지에.';
CREATE INDEX ix_resources_club_folder_created ON resources (club_id, folder, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_resources_club_created ON resources (club_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_resources_uploader ON resources (uploader_id);

CREATE TABLE resource_cohorts (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resource_id bigint NOT NULL REFERENCES resources (id) ON DELETE CASCADE,
    cohort_id   bigint NOT NULL REFERENCES cohorts (id)   ON DELETE CASCADE
);
COMMENT ON TABLE resource_cohorts IS 'COHORT_ONLY 자료의 공개 대상 기수 junction(ResourceFile.cohortIds). 다른 컨텍스트처럼 jsonb 배열 대신 FK 모델.';
CREATE UNIQUE INDEX ux_resource_cohorts_resource_cohort ON resource_cohorts (resource_id, cohort_id);
CREATE INDEX ix_resource_cohorts_cohort ON resource_cohorts (cohort_id);

-- =====================================================================
-- 8) Settings · Subscription · Admin · Notification
-- =====================================================================
CREATE TABLE subscription_plans (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tier        varchar(20) NOT NULL CHECK (tier IN ('FREE','STANDARD','PRO')),
    name        varchar(50) NOT NULL,
    price_krw   integer     NOT NULL,                            -- priceLabel 파생
    member_limit integer    NOT NULL,                            -- memberLimitLabel 파생
    recommended boolean     NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE subscription_plans IS '구독 플랜 정적 카탈로그(reference data). Flyway 시드/관리자 편집. plans()가 DB에서 읽음.';
CREATE UNIQUE INDEX ux_subscription_plans_tier ON subscription_plans (tier);

CREATE TABLE plan_features (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id    bigint       NOT NULL REFERENCES subscription_plans (id) ON DELETE CASCADE,
    included   boolean      NOT NULL,
    text       varchar(200) NOT NULL,
    sort_order integer      NOT NULL DEFAULT 0,
    created_at timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE plan_features IS '플랜별 기능 체크리스트(27 카드).';
CREATE INDEX ix_plan_features_plan ON plan_features (plan_id, sort_order);

CREATE TABLE subscriptions (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    club_id         bigint      NOT NULL REFERENCES clubs (id) ON DELETE CASCADE,
    tier            varchar(20) NOT NULL DEFAULT 'FREE' CHECK (tier IN ('FREE','STANDARD','PRO')),
    member_limit    integer     NOT NULL,                        -- 계약 시점 플랜값 스냅샷(플랜 개정에도 보존)
    status          varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CANCELED')),
    started_at      timestamptz,                                 -- 유료 전환 시각(FREE면 NULL)
    next_billing_at timestamptz,
    canceled_at     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE subscriptions IS '동아리 1곳의 현재 구독(club 1:1). member_limit은 플랜값 스냅샷. memberUsed는 club_members COUNT로 파생.';
CREATE UNIQUE INDEX ux_subscriptions_club ON subscriptions (club_id);

CREATE TABLE payment_records (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subscription_id bigint      NOT NULL REFERENCES subscriptions (id) ON DELETE CASCADE,
    title           varchar(100) NOT NULL,                       -- ★감사 픽스: 결제시점 플랜명 스냅샷('스탠다드 플랜 결제'). tier 파생 시 플랜변경 후 과거 이력 오라벨
    amount_krw      integer     NOT NULL,                        -- amountLabel 파생
    channel         varchar(30) NOT NULL,                        -- 'App Store'/'Google Play'/'Card'. 값집합 미확정 → CHECK 생략
    paid_at         timestamptz NOT NULL,                        -- dateLabel 파생
    created_at      timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE payment_records IS '구독 결제 이력(구독당 N건).';
CREATE INDEX ix_payment_records_subscription ON payment_records (subscription_id, paid_at DESC);

CREATE TABLE admin_profiles (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    club_member_id bigint      NOT NULL REFERENCES club_members (id) ON DELETE CASCADE, -- 1:1 확장
    title          varchar(30) NOT NULL,                         -- '부회장'/'총무'
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE admin_profiles IS '운영진 프로필(club_members 1:1 확장). member_role IN(STAFF,LEADER)에 직함 부여. 권한셋/직함은 이 컨텍스트 고유라 club_members에서 분리.';
CREATE UNIQUE INDEX ux_admin_profiles_club_member ON admin_profiles (club_member_id);

CREATE TABLE admin_permissions (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_profile_id bigint      NOT NULL REFERENCES admin_profiles (id) ON DELETE CASCADE,
    permission_type  varchar(30) NOT NULL CHECK (permission_type IN
                        ('NOTICE_WRITE','JOIN_APPROVE','BOARD_MANAGE','MEMBER_MANAGE','SCHEDULE_MANAGE','CLUB_SETTINGS')),
    created_at       timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE admin_permissions IS '운영진 위임 권한 junction(6종). togglePermission=insert/delete.';
CREATE UNIQUE INDEX ux_admin_permissions_profile_type ON admin_permissions (admin_profile_id, permission_type);
CREATE INDEX ix_admin_permissions_profile ON admin_permissions (admin_profile_id);

CREATE TABLE blocked_users (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    club_id         bigint      NOT NULL REFERENCES clubs (id) ON DELETE CASCADE,
    blocked_user_id bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    is_withdrawn    boolean     NOT NULL DEFAULT false,          -- 차단 대상이 탈퇴회원인지 표시(83 배지)
    blocked_at      timestamptz NOT NULL DEFAULT now(),
    created_at      timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE blocked_users IS '동아리 차단 명단(club 단위). unblock=하드delete(소프트 불필요).';
CREATE UNIQUE INDEX ux_blocked_users_club_user ON blocked_users (club_id, blocked_user_id);
CREATE INDEX ix_blocked_users_club ON blocked_users (club_id, blocked_at DESC);

CREATE TABLE notification_settings (
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id               bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE, -- 유저 1:1 전역
    push                  boolean     NOT NULL DEFAULT true,
    new_post              boolean     NOT NULL DEFAULT true,
    comment               boolean     NOT NULL DEFAULT true,
    schedule_reminder     boolean     NOT NULL DEFAULT true,
    join_request          boolean     NOT NULL DEFAULT true,     -- 운영 토글(노출은 role로 앱단 제어)
    event_apply           boolean     NOT NULL DEFAULT true,
    reminder_days_before  integer,                               -- reminderLabel 파생
    reminder_hours_before integer,
    dnd_enabled           boolean     NOT NULL DEFAULT false,
    dnd_start             time,                                  -- dndRangeLabel 파생
    dnd_end               time,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE notification_settings IS '유저별 알림 환경설정(전역, observeNotifSettings에 club 인자 없음). updateNotifSettings=upsert.';
CREATE UNIQUE INDEX ux_notification_settings_user ON notification_settings (user_id);

CREATE TABLE notifications (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint       NOT NULL REFERENCES users (id) ON DELETE CASCADE, -- 수신자
    club_id    bigint       REFERENCES clubs (id) ON DELETE CASCADE,          -- 시스템 알림은 NULL
    type       varchar(20)  NOT NULL CHECK (type IN ('JOIN_APPROVED','NOTICE','COMMENT','SCHEDULE','VOTE')),
    text       varchar(500) NOT NULL,
    is_read    boolean      NOT NULL DEFAULT false,
    created_at timestamptz  NOT NULL DEFAULT now()               -- timeAgo 파생, 최신순 정렬
);
COMMENT ON TABLE notifications IS '유저 수신 알림. markAllRead=미읽음 일괄 UPDATE. 벨 배지=is_read=false COUNT 파생.';
CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id, is_read) WHERE is_read = false;


-- =====================================================================
-- 9) 검수 픽스: 누락된 FK 커버링 인덱스 (Postgres는 FK 자동 인덱스 없음)
-- =====================================================================
CREATE INDEX ix_notifications_club     ON notifications     (club_id);
CREATE INDEX ix_blocked_users_blocked  ON blocked_users     (blocked_user_id);
CREATE INDEX ix_users_active_club      ON users             (active_club_id);
CREATE INDEX ix_join_apps_decided_by   ON join_applications (decided_by);
CREATE INDEX ix_post_drafts_club       ON post_drafts       (club_id);

