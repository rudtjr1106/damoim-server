-- ─────────────────────────────────────────────────────────────────────────────
-- V5: 알림 → 관련 화면 이동 대상
--   · 알림 목록(37)에서 항목을 누르면 관련 글/일정으로 이동하도록 대상 좌표를 저장
--   · target_type NULL = 이동 대상 없음(JOIN_APPROVED 등 → 홈 유지)
--   · 다형 참조(posts/schedules)라 FK는 걸지 않는다. 대상 소실은 조회 시 404로 처리.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE notifications ADD COLUMN target_type varchar(20);   -- POST | SCHEDULE | NULL
ALTER TABLE notifications ADD COLUMN target_id   bigint;        -- 대상 id(타입별 해석)

COMMENT ON COLUMN notifications.target_type IS '이동 대상 종류(POST/SCHEDULE). NULL이면 이동 없음.';
COMMENT ON COLUMN notifications.target_id   IS '이동 대상 id. target_type과 항상 동반(둘 다 NULL이거나 둘 다 NOT NULL).';

-- 반쪽 데이터(타입만/ id만)로 클라 네비가 깨지는 것을 DB에서 원천 차단.
-- 기존 행은 둘 다 NULL이라 그대로 통과(백필 불필요).
-- ⚠️ `target_type IS NOT NULL` 가드가 필수 — 없으면 target_type=NULL일 때 `NULL IN (...)`이 NULL로 평가되고
--    CHECK은 FALSE일 때만 거부하므로(NULL=통과) id만 있는 반쪽 행이 그대로 들어온다.
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_target
    CHECK (
        (target_type IS NULL AND target_id IS NULL) OR
        (target_type IS NOT NULL AND target_type IN ('POST','SCHEDULE') AND target_id IS NOT NULL)
    );
