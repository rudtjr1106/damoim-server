-- =====================================================================
-- 게시판 첨부를 저장 쿼터에 합산하기 위한 size_bytes 정합성 확보.
--   그동안 IMAGE 첨부는 크기를 실측(S3 HeadObject)해놓고 버려서 size_bytes가 NULL이었고,
--   쿼터는 자료실 SUM만 봤다 → FREE 동아리도 게시글 첨부로 스토리지를 무제한 사용 가능,
--   설정 화면 사용량에도 안 잡힘. 서버가 이제 IMAGE에도 크기를 항상 기록하므로 DB에서도 못 박는다.
-- 규칙(V1 헤더): snake_case / enum=varchar+CHECK / 시각=timestamptz
-- =====================================================================

-- ⚠️ 기존 IMAGE 행은 size_bytes가 NULL이라 NOT NULL 제약을 그냥 걸면 마이그레이션이 실패한다.
-- 실제 크기는 오브젝트 스토리지에만 있어 SQL로는 알 수 없으므로 0으로 백필한다.
-- (제약 완화 대신 백필을 택한 이유: SUM은 NULL을 무시하므로 0 백필은 사용량 계산 결과를 바꾸지 않고
--  — 레거시 분은 어느 쪽이든 미집계 — 앞으로 들어올 "크기 없는 첨부"만 원천 차단할 수 있다.
--  제약을 완화하면 같은 회귀(크기 누락)가 조용히 다시 들어온다.)
UPDATE post_attachments SET size_bytes = 0 WHERE type = 'IMAGE' AND size_bytes IS NULL;

-- 제약 갱신: IMAGE도 size_bytes 필수(V3에선 storage_key만 요구했다).
ALTER TABLE post_attachments DROP CONSTRAINT ck_post_attach_type_fields;
ALTER TABLE post_attachments ADD CONSTRAINT ck_post_attach_type_fields CHECK (
    (type = 'IMAGE'    AND storage_key IS NOT NULL AND size_bytes IS NOT NULL) OR
    (type = 'FILE_DOC' AND file_name IS NOT NULL AND size_bytes IS NOT NULL AND storage_key IS NOT NULL) OR
    (type = 'LINK'     AND link_title IS NOT NULL AND link_domain IS NOT NULL AND link_url IS NOT NULL)
);

-- 음수 크기는 쿼터 합계를 깎아 우회 수단이 된다. LINK는 NULL이라 통과(NULL 비교 → unknown = CHECK 통과).
ALTER TABLE post_attachments ADD CONSTRAINT ck_post_attach_size_nonneg CHECK (size_bytes IS NULL OR size_bytes >= 0);

COMMENT ON COLUMN post_attachments.size_bytes IS 'IMAGE/FILE_DOC 실제 오브젝트 크기(bytes). 자료실과 합쳐 동아리 저장 쿼터에 합산(레거시 IMAGE는 크기 불명이라 0 백필). LINK는 NULL.';

-- 쿼터 합산은 동아리의 살아있는 글 전체를 훑는다(post_attachments ⋈ board_posts).
-- board_posts 쪽은 ix_board_posts_club_created(부분 인덱스)가 커버하고, 조인 대상인 post_id는
-- ix_post_attachments_post(post_id, position)가 커버한다 — 새 인덱스는 필요 없다.
