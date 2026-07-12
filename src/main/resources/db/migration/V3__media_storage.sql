-- ─────────────────────────────────────────────────────────────────────────────
-- V3: 실제 파일/이미지 스토리지(S3) 연동
--   · 게시판 첨부(IMAGE/FILE_DOC)를 라벨이 아닌 실제 S3 오브젝트 키로 저장
--   · 게시판 링크 첨부에 전체 URL 보존(웹 이동용 — 기존엔 title/domain만)
--   · 프로필 사진을 내부 업로드 S3 키로 저장(외부 http URL=카카오는 profile_image_url 유지)
-- 파일 바이너리는 DB 밖 오브젝트 스토리지에, 여기엔 키만. 읽을 때 presigned URL로 서빙.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── 게시판 첨부 ──
ALTER TABLE post_attachments ADD COLUMN storage_key text;   -- IMAGE/FILE_DOC S3 오브젝트 키
ALTER TABLE post_attachments ADD COLUMN link_url    text;   -- LINK 전체 URL(웹 이동용)

-- 레거시 정리: 실제 파일이 없던(라벨만/URL만 없던) 기존 첨부는 새 제약을 만족하지 못하고
-- 가리키는 실오브젝트도 없으므로 제거(출시 전 데이터라 안전).
DELETE FROM post_attachments WHERE type = 'IMAGE'    AND storage_key IS NULL;
DELETE FROM post_attachments WHERE type = 'FILE_DOC' AND storage_key IS NULL;
DELETE FROM post_attachments WHERE type = 'LINK'     AND link_url    IS NULL;

-- 제약 갱신: IMAGE/FILE_DOC=storage_key 필수, LINK=link_url 필수. image_label은 이제 선택(캡션).
ALTER TABLE post_attachments DROP CONSTRAINT ck_post_attach_type_fields;
ALTER TABLE post_attachments ADD CONSTRAINT ck_post_attach_type_fields CHECK (
    (type = 'IMAGE'    AND storage_key IS NOT NULL) OR
    (type = 'FILE_DOC' AND file_name IS NOT NULL AND size_bytes IS NOT NULL AND storage_key IS NOT NULL) OR
    (type = 'LINK'     AND link_title IS NOT NULL AND link_domain IS NOT NULL AND link_url IS NOT NULL)
);
COMMENT ON COLUMN post_attachments.storage_key IS 'IMAGE/FILE_DOC의 S3 오브젝트 키(posts/{clubId}/{uuid}/{name}). 읽을 때 presigned URL로 서빙.';
COMMENT ON COLUMN post_attachments.link_url IS 'LINK 첨부의 전체 URL(클릭 시 웹 이동).';

-- ── 프로필 사진 ──
ALTER TABLE users ADD COLUMN profile_image_key text;        -- 내부 업로드 S3 키(profiles/{userId}/{uuid}/{name})
COMMENT ON COLUMN users.profile_image_key IS '앱에서 올린 프로필 사진의 S3 키. 있으면 응답 profileImageUrl은 presigned view URL로 파생(외부 카카오 URL은 profile_image_url 유지).';
