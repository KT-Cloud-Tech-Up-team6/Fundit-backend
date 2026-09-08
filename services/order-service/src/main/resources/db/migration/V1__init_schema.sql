-- ============================================================
-- 4. order-service (재고/펀딩(주문)/쿠폰)
-- ============================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE inventories (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reward_id        BIGINT NOT NULL,       -- catalog-service(rewards.id) 참조, FK 아님
    available_stock  INT NOT NULL CHECK (available_stock >= 0),
    reserved_stock   INT NOT NULL DEFAULT 0 CHECK (reserved_stock >= 0),
    version          INT NOT NULL DEFAULT 0,   -- 낙관적 락(Optimistic Lock)용
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_inventories_reward ON inventories (reward_id);
CREATE TRIGGER trg_inventories_updated_at
    BEFORE UPDATE ON inventories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE reward_restock_notify_requests (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reward_id         BIGINT NOT NULL,      -- catalog-service(rewards.id) 참조, FK 아님
    member_id         UUID NOT NULL,        -- member-service 참조, FK 아님
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_reward_restock_notify ON reward_restock_notify_requests (reward_id, member_id);

CREATE TABLE fundings (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id           UUID NOT NULL,          -- 애플리케이션에서 생성한 UUID, 외부 노출용(orderId로 사용)
    member_id           UUID NOT NULL,          -- member-service 참조, FK 아님
    project_id          BIGINT NOT NULL,        -- catalog-service 참조, FK 아님
    live_session_id     BIGINT,                 -- live-service 참조, FK 아님
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','FUNDING_IN_PROGRESS','CANCELLED_BY_MEMBER',
                                           'PAYMENT_EXPIRED',
                                           'GOAL_FAILED_REFUNDED','GOAL_ACHIEVED',
                                           'REFUNDED_AFTER_SUCCESS')),
    shipping_address    JSONB NOT NULL,
    shipping_fee        BIGINT NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
    payment_expires_at  TIMESTAMP NOT NULL,
    decided_at          TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_fundings_public_id ON fundings (public_id);
CREATE INDEX idx_fundings_member ON fundings (member_id);
CREATE INDEX idx_fundings_project ON fundings (project_id);
CREATE INDEX idx_fundings_status ON fundings (status);
-- [신규 추가] 만료 배치(ORDER-013)가 대상 조회 시 사용
CREATE INDEX idx_fundings_pending_expiry ON fundings (payment_expires_at) WHERE status = 'PENDING';
CREATE TRIGGER trg_fundings_updated_at
    BEFORE UPDATE ON fundings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
-- API 라우팅·소비자/판매자 화면의 orderId는 public_id(UUID)를 사용하고,
-- 서비스 간 통신(FK 참조 등)에는 내부 id(BIGINT)를 사용한다(live_sessions와 동일 패턴).

CREATE TABLE funding_line_items (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    funding_id       BIGINT NOT NULL,
    reward_id        BIGINT NOT NULL,        -- catalog-service(rewards.id) 참조, FK 아님
    quantity         INT NOT NULL CHECK (quantity > 0),
    unit_price       BIGINT NOT NULL CHECK (unit_price >= 0),
    CONSTRAINT fk_funding_line_items_funding FOREIGN KEY (funding_id) REFERENCES fundings(id)
);
CREATE INDEX idx_funding_line_items_funding ON funding_line_items (funding_id);
CREATE INDEX idx_funding_line_items_reward ON funding_line_items (reward_id);

CREATE TABLE funding_line_item_options (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    funding_line_item_id  BIGINT NOT NULL,
    option_group_id       BIGINT NOT NULL,   -- catalog-service(reward_option_groups.id) 참조, FK 아님
    option_group_name     VARCHAR(50) NOT NULL,  -- 스냅샷, 예: 색상
    option_value_id       BIGINT NOT NULL,   -- catalog-service(reward_option_values.id) 참조, FK 아님
    option_value          VARCHAR(50) NOT NULL,  -- 스냅샷, 예: 화이트
    CONSTRAINT fk_funding_line_item_options_item FOREIGN KEY (funding_line_item_id) REFERENCES funding_line_items(id)
);
CREATE INDEX idx_funding_line_item_options_item ON funding_line_item_options (funding_line_item_id);
CREATE UNIQUE INDEX uq_funding_line_item_options_group ON funding_line_item_options (funding_line_item_id, option_group_id);

CREATE TABLE coupons (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    coupon_code            VARCHAR(30) NOT NULL,
    coupon_name            VARCHAR(100) NOT NULL,
    -- [정책 확정] 배송비를 받는 구조로 확정. discount_type='FREE_SHIPPING'인 쿠폰은
    -- discount_value를 사용하지 않고(0으로 저장), 적용 시점에 해당 funding의
    -- shipping_fee를 할인액으로 계산한다(fundings 테이블 코멘트 참고).
    discount_type          VARCHAR(15) NOT NULL CHECK (discount_type IN ('RATE','AMOUNT','FREE_SHIPPING')),
    discount_value         BIGINT NOT NULL CHECK (discount_value >= 0),
    max_discount_amount    BIGINT CHECK (max_discount_amount >= 0),
    -- [신규 추가] 발급 개수(remaining_quantity)만으로는 정률 쿠폰의 총 할인 예산을
    -- 예측할 수 없어 별도 예산 필드를 둔다. budget_limit이 NULL이면 예산 한도 미설정.
    budget_limit           BIGINT CHECK (budget_limit >= 0),
    used_budget_amount     BIGINT NOT NULL DEFAULT 0 CHECK (used_budget_amount >= 0),
    issuer_type            VARCHAR(10) NOT NULL CHECK (issuer_type IN ('PLATFORM','MAKER')),
    issuer_id              UUID,             -- MAKER 발급 시 seller_id, PLATFORM이면 NULL
    target_scope           VARCHAR(20) NOT NULL DEFAULT 'ALL'
                           CHECK (target_scope IN ('ALL','CATEGORY','PROJECT','MAKER')),
    target_ref_id          VARCHAR(50),
    min_funding_amount     BIGINT NOT NULL DEFAULT 0 CHECK (min_funding_amount >= 0),
    per_member_limit       INT NOT NULL DEFAULT 1 CHECK (per_member_limit > 0),
    remaining_quantity     INT NOT NULL CHECK (remaining_quantity >= 0),
    expires_at             TIMESTAMP NOT NULL,
    issue_channel          VARCHAR(10) NOT NULL DEFAULT 'GENERAL'
                           CHECK (issue_channel IN ('GENERAL','LIVE')),
    live_session_id        BIGINT,           -- live-service 참조, FK 아님
    drop_type               VARCHAR(20)
                           CHECK (drop_type IN ('FIRST_COME','MANUAL_DROP','WATCH_TIME_AUTO')),
    version                 INT NOT NULL DEFAULT 0,   -- 낙관적 락(Optimistic Lock)용
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_coupons_code ON coupons (coupon_code);
CREATE INDEX idx_coupons_issuer ON coupons (issuer_type, issuer_id);
CREATE INDEX idx_coupons_live_session ON coupons (live_session_id);

CREATE TABLE coupon_issuances (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    coupon_code     VARCHAR(30) NOT NULL,
    owner_id        UUID NOT NULL,          -- member-service 참조, FK 아님
    issued_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status          VARCHAR(10) NOT NULL DEFAULT 'AVAILABLE'
                    CHECK (status IN ('AVAILABLE','USED','EXPIRED')),
    used_funding_id BIGINT,
    used_at         TIMESTAMP,
    restored_at     TIMESTAMP,
    CONSTRAINT fk_coupon_issuances_coupon FOREIGN KEY (coupon_code) REFERENCES coupons(coupon_code)
);
CREATE INDEX idx_coupon_issuances_owner ON coupon_issuances (owner_id);
CREATE INDEX idx_coupon_issuances_status ON coupon_issuances (status);

CREATE TABLE funding_coupon_applications (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    funding_id         BIGINT NOT NULL,
    coupon_issuance_id BIGINT NOT NULL,
    discount_amount    BIGINT NOT NULL CHECK (discount_amount >= 0),
    applied_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_funding_coupon_applications_funding FOREIGN KEY (funding_id) REFERENCES fundings(id),
    CONSTRAINT fk_funding_coupon_applications_issuance FOREIGN KEY (coupon_issuance_id) REFERENCES coupon_issuances(id)
);
CREATE UNIQUE INDEX uq_funding_coupon_applications ON funding_coupon_applications (funding_id, coupon_issuance_id);
CREATE INDEX idx_funding_coupon_applications_funding ON funding_coupon_applications (funding_id);