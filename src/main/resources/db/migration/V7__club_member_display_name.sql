-- =====================================================================
-- 동아리별 프로필(44). 그동안 표시 이름은 전역 users.nickname 하나라 모든 동아리에서
-- 같은 이름으로 보였다. club_members에 per-membership 표시 이름/사진 오버라이드를 두어
-- 같은 사용자가 A동아리에선 '조경석', B동아리에선 '조나단'으로 보일 수 있게 한다.
-- 둘 다 NULL이면 users 값으로 폴백한다.
-- =====================================================================
ALTER TABLE club_members ADD COLUMN display_name      varchar(50);   -- NULL=users.nickname 폴백
ALTER TABLE club_members ADD COLUMN display_image_key text;          -- NULL=users 프로필 사진 폴백(현재 MVP는 미해석)

COMMENT ON COLUMN club_members.display_name IS '동아리별 표시 이름 오버라이드(44). NULL이면 users.nickname 사용.';
COMMENT ON COLUMN club_members.display_image_key IS '동아리별 프로필 사진 키 오버라이드(44). NULL이면 users 사진 사용.';
