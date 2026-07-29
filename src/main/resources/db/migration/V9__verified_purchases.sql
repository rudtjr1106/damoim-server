-- 인앱결제 영수증 재사용 차단 대장 — "영수증 1건 = 구독 1회".
-- 검증에 성공한 스토어 트랜잭션을 여기에 남겨, 같은 영수증으로 다른 동아리 PRO를 또 켜는 우회를
-- DB 유니크 제약으로 막는다(앱 레벨 조회만으로는 동시 요청 경합을 못 막음).
-- 규칙(V1 헤더): snake_case / 복수형 / PK bigint GENERATED ALWAYS AS IDENTITY / enum=varchar+CHECK / 시각=timestamptz
CREATE TABLE verified_purchases (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    platform       varchar(20)  NOT NULL CHECK (platform IN ('APP_STORE', 'PLAY')),
    transaction_id varchar(120) NOT NULL,   -- 스토어 발급 트랜잭션 고유 ID(App Store=JWS transactionId, Play=orderId)
    product_id     varchar(200) NOT NULL,   -- 검증된 상품 ID 스냅샷(사후 감사용)
    tier           varchar(20)  NOT NULL CHECK (tier IN ('FREE', 'STANDARD', 'PRO')),  -- 이 증빙으로 켠 플랜
    created_at     timestamptz  NOT NULL DEFAULT now()                                  -- = 검증 성공 시각
);

-- 재사용 차단의 실제 방어선. 구독 트랜잭션 안에서 INSERT하므로 결제 실패/롤백 시 기록도 함께 사라진다.
CREATE UNIQUE INDEX ux_verified_purchases_platform_txn ON verified_purchases (platform, transaction_id);

COMMENT ON TABLE verified_purchases IS '검증 성공한 인앱결제 트랜잭션 대장(영수증 재사용 차단). 구독 활성화 직전 PurchaseVerifier가 기록.';
