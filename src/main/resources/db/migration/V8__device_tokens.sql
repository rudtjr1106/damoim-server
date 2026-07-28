-- FCM 디바이스 토큰 — 유저별 푸시 발송 대상.
-- 규칙(V1 헤더): snake_case / 복수형 / PK bigint GENERATED ALWAYS AS IDENTITY / enum=varchar+CHECK / 시각=timestamptz
CREATE TABLE device_tokens (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token      text NOT NULL,
    platform   varchar(16) CHECK (platform IN ('ANDROID', 'IOS')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- 같은 토큰은 한 행만(재로그인 시 소유자 재할당) + 발송 시 user_id 조회 커버링.
CREATE UNIQUE INDEX ux_device_tokens_token ON device_tokens (token);
CREATE INDEX ix_device_tokens_user ON device_tokens (user_id);

COMMENT ON TABLE device_tokens IS 'FCM 푸시 발송 대상 디바이스 토큰(유저당 N개, 기기별)';
