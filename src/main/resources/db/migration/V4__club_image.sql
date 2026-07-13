-- ─────────────────────────────────────────────────────────────────────────────
-- V4: 동아리 대표 이미지(로고) S3 연동
--   · 동아리 정보 설정(08)에서 대표 이미지를 바꿀 수 있도록 내부 업로드 S3 키를 저장
--   · 바이너리는 오브젝트 스토리지에, 여기엔 키만. 읽을 때 presigned view URL로 서빙.
-- (프로필 사진 users.profile_image_key와 동일한 패턴)
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE clubs ADD COLUMN image_key text;   -- 내부 업로드 S3 키(clubs/{clubId}/{uuid}/{name})
COMMENT ON COLUMN clubs.image_key IS '동아리 대표 이미지의 S3 키. 있으면 응답 imageUrl은 presigned view URL로 파생.';
