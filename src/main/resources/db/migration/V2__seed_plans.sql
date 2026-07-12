-- =====================================================================
-- 구독 플랜 정적 카탈로그(reference data). 27 구독 플랜 카드가 이 데이터를 읽는다.
-- priceLabel·memberLimitLabel은 서버가 price_krw·member_limit에서 파생한다.
-- =====================================================================
INSERT INTO subscription_plans (tier, name, price_krw, member_limit, recommended) VALUES
    ('FREE',     '무료',     0,     30,   false),
    ('STANDARD', '스탠다드', 9900,  100,  true),
    ('PRO',      '프로',     19900, 9999, false);

INSERT INTO plan_features (plan_id, included, text, sort_order)
SELECT p.id, v.included, v.text, v.sort_order
FROM subscription_plans p
JOIN (VALUES
    ('FREE',     true,  '회원 30명 미만',                      0),
    ('FREE',     true,  '게시판·일정·회원 관리 등 핵심 기능',   1),
    ('FREE',     false, '파일 저장 용량 1GB',                  2),
    ('STANDARD', true,  '회원 100명까지',                      0),
    ('STANDARD', true,  '모든 핵심 기능 + 공지 확인율 통계',    1),
    ('STANDARD', true,  '파일 저장 용량 20GB',                 2),
    ('PRO',      true,  '회원 무제한',                         0),
    ('PRO',      true,  '스탠다드 전체 + 우선 지원',           1),
    ('PRO',      true,  '파일 저장 용량 100GB',                2)
) AS v(tier, included, text, sort_order) ON p.tier = v.tier;
