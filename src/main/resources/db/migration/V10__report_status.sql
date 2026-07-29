-- 신고 처리 상태 — App Store 1.2 / Google Play UGC 정책은 "신고 접수"만으로 부족하고
-- 운영진이 실제로 조치했다는 기록(누가·언제·무슨 조치)을 요구한다. post_reports에는 접수 시각만 있어
-- 대기/처리완료/기각 전이와 감사 컬럼을 덧붙인다.
-- 규칙(V1 헤더): snake_case / enum=varchar+CHECK / 시각=timestamptz
ALTER TABLE post_reports
    ADD COLUMN status     varchar(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RESOLVED', 'REJECTED')),
    -- 처리 시 취한 조치. 미처리(PENDING) 건은 NULL이라 CHECK를 통과한다(NULL IN → unknown).
    ADD COLUMN action     varchar(20)
        CHECK (action IN ('NONE', 'DELETE_CONTENT')),
    ADD COLUMN handled_by bigint REFERENCES users (id) ON DELETE SET NULL,  -- 처리한 운영진(탈퇴해도 기록 보존)
    ADD COLUMN handled_at timestamptz;

COMMENT ON COLUMN post_reports.status IS '신고 처리 상태(기본 PENDING). 운영진이 RESOLVED(처리완료)/REJECTED(기각)로 전이.';
COMMENT ON COLUMN post_reports.action IS '처리 시 취한 조치(NONE=조치 없음, DELETE_CONTENT=대상 글/댓글 삭제). 미처리면 NULL.';
COMMENT ON COLUMN post_reports.handled_by IS '처리한 운영진 user_id(감사 기록).';

-- 35 운영진 신고함은 대기 건부터 처리한다 — 대기 목록만 커버하는 부분 인덱스.
CREATE INDEX ix_post_reports_pending ON post_reports (status) WHERE status = 'PENDING';
-- FK 커버링 인덱스(Postgres는 FK 자동 인덱스 없음 — V1 9)절과 동일 규칙).
CREATE INDEX ix_post_reports_handled_by ON post_reports (handled_by);
