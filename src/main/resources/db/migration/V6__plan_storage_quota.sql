-- =====================================================================
-- 플랜별 파일 저장 용량(41). 그동안 스토리지 쿼터는 app.storage.quota-bytes(5GB)
-- 전역 고정이라 플랜과 무관했다. 플랜 카탈로그가 광고하던 값(FREE 1GB / STANDARD 20GB
-- / PRO 100GB)을 실제 컬럼으로 만들어 집행한다. member_limit과 동일하게 subscriptions에도
-- 계약 시점 스냅샷을 둔다(플랜 개정에도 기존 계약 보존).
-- =====================================================================

-- 플랜 카탈로그: 저장 용량(bytes). 기본 1GB, 아래 UPDATE로 티어별 시드.
ALTER TABLE subscription_plans ADD COLUMN storage_quota_bytes bigint NOT NULL DEFAULT 1073741824;

UPDATE subscription_plans SET storage_quota_bytes = 1073741824   WHERE tier = 'FREE';      -- 1GB
UPDATE subscription_plans SET storage_quota_bytes = 21474836480  WHERE tier = 'STANDARD';  -- 20GB
UPDATE subscription_plans SET storage_quota_bytes = 107374182400 WHERE tier = 'PRO';       -- 100GB

-- 동아리 구독: 계약 시점 스냅샷(member_limit 패턴). 기본 1GB 후 기존 행을 플랜값으로 백필.
ALTER TABLE subscriptions ADD COLUMN storage_quota_bytes bigint NOT NULL DEFAULT 1073741824;

UPDATE subscriptions s
SET storage_quota_bytes = p.storage_quota_bytes
FROM subscription_plans p
WHERE p.tier = s.tier;
