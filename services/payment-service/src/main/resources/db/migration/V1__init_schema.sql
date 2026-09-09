-- ============================================================
-- payment-service — 결제/환불/정산 스키마 (payment / refund / settlement)
-- 출처: PaymentERD.md(2026-09-08 개정) 3장 DDL을 그대로 반영.
-- [payment-service CLAUDE.md 반영] payment.payments.coupon_issuance_id 컬럼을
-- ERD에 없던 값으로 신규 추가했다 — order-service PaymentEventListener 계약
-- (PaymentCompletedEvent/RefundCompletedEvent)이 couponIssuanceId를 요구하는데,
-- 이 값은 order-service의 funding_coupon_applications에만 있어 PAYMENT-001 시점에
-- OrderFundingClient 응답으로 받아 스냅샷해두지 않으면 PAYMENT-002/004/005/007/008의
-- 이벤트 발행 시점에 구할 방법이 없다(CLAUDE.md "⚠️ 가장 먼저 읽을 것" 참고).
-- [정책 확인 필요] refund.refund_requests.trigger_type에 PAYMENT-017(레이스 컨디션
-- 자동환불) 전용 값 'SYSTEM_RECONCILIATION'을 추가했다 — CLAUDE.md 6장이 제안한 값을
-- 그대로 사용했으며, 최종 확정은 아니다.
-- [신규] payment.payments.pg_secret 컬럼을 ERD에 없던 값으로 추가했다 — 토스 결제승인
-- API 응답의 Payment 객체에 포함되는 secret 값(웹훅 이벤트 유효성 검증용, 문서:
-- https://docs.tosspayments.com/reference/using-api/webhook-events)을 승인 시점에
-- 저장해뒀다가, 웹훅 수신 시(1-3, PAYMENT_STATUS_CHANGED/CANCEL_STATUS_CHANGED)
-- payload의 secret과 대조해 위조 요청을 걸러낸다(security.md S7 "외부 응답값은 검증 후
-- 사용" / API 명세서 1-3 "서명 검증 필수"의 실제 구현 방식).
-- [신규] payment.payments.member_id 컬럼을 ERD에 없던 값으로 추가했다 — ERD 어디에도
-- 결제 건의 소유자(member_id)를 저장하는 컬럼이 없어, PAYMENT-002(승인) 시점의 소유권
-- 재검증(security.md S4)과 PAYMENT-003(본인 환불내역 조회)을 order-service 동기 호출 없이
-- 구현할 방법이 없었다. PAYMENT-001 시점 order-service 내부 API 응답의 memberId를 그대로
-- 스냅샷한다(amount/orderName과 동일한 방식).
-- ============================================================

CREATE SCHEMA payment;
CREATE SCHEMA refund;
CREATE SCHEMA settlement;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- payment.payments
-- [결제위젯] pg_order_id는 결제위젯 widgets.requestPayment({ orderId, ... }) 호출에
-- 그대로 전달되는 값이다. customerKey는 이 테이블에 저장하지 않는다 — member_id(UUID)를
-- 프론트엔드가 그대로 사용하므로 백엔드가 별도 발급/저장할 필요가 없다.
-- payment_method는 대분류만 저장하고(카드/가상계좌/계좌이체/간편결제), 간편결제 브랜드
-- (카카오페이/네이버페이/삼성페이 등)는 easy_pay_provider에 별도 저장한다.
-- funding_id 기준 order-service의 memberId/status/finalAmount/orderName/couponIssuanceId는
-- PAYMENT-001 시점에 order-service 내부 API를 동기 호출해 스냅샷으로 고정 저장한다.
-- Funding.status 변경은 이 테이블/이 서비스가 직접 수행하지 않는다 — payment_event_outbox로
-- 이벤트만 발행한다.
-- ============================================================
CREATE TABLE payment.payments (
    id                   UUID NOT NULL PRIMARY KEY,   -- 애플리케이션(Spring)에서 생성한 UUID v7을 그대로 INSERT
    funding_id           BIGINT NOT NULL,             -- order-service 참조, FK 아님
    member_id            UUID NOT NULL,               -- [신규] order-service 참조(members.id), FK 아님. PAYMENT-001 시점 스냅샷
    pg_order_id          VARCHAR(64) NOT NULL,        -- 위젯 requestPayment()에 전달하는 가맹점 주문번호(영문 대소문자/숫자/-/_, 6~64자), PAYMENT-001에서 채번
    pg_payment_key       VARCHAR(200),                -- 토스 발급 paymentKey. 승인 성공 후에만 채워짐
    pg_secret            VARCHAR(64),                 -- [신규] 토스 승인 응답 Payment.secret — 웹훅 유효성 검증용, 승인 성공 후에만 채워짐
    amount               BIGINT NOT NULL CHECK (amount >= 0),  -- PAYMENT-001 시점 order-service 내부 API 응답의 finalAmount 스냅샷
    order_name           VARCHAR(100) NOT NULL,       -- PAYMENT-001 시점 order-service 내부 API 응답을 그대로 저장(위젯 requestPayment에 재사용)
    coupon_issuance_id   BIGINT,                      -- [신규] order-service 참조(coupon_issuances.id), FK 아님. 쿠폰 미적용 주문이면 NULL
    payment_method       VARCHAR(20)
                        CHECK (payment_method IN ('CARD','VIRTUAL_ACCOUNT','TRANSFER','EASY_PAY')),
                        -- 승인 전에는 NULL 허용(위젯에서 아직 결제수단을 선택하기 전 시점 존재)
    easy_pay_provider    VARCHAR(20),                 -- payment_method='EASY_PAY'일 때만: TOSSPAY/KAKAOPAY/NAVERPAY/SAMSUNGPAY_CARD 등
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','COMPLETED','FAILED','CANCELLED')),
    idempotency_key      VARCHAR(100) NOT NULL,       -- 승인 API 호출 시 사용하는 애플리케이션 레벨 멱등키(재시도 시 중복 승인 방지)
    paid_at              TIMESTAMP,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- status='COMPLETED'일 때만 funding_id 값을 갖는 생성 컬럼 (MySQL IF() → Postgres CASE WHEN)
    completed_funding_id BIGINT GENERATED ALWAYS AS (
                             CASE WHEN status = 'COMPLETED' THEN funding_id ELSE NULL END
                         ) STORED
);
CREATE UNIQUE INDEX uq_payments_completed_funding ON payment.payments (completed_funding_id);
CREATE UNIQUE INDEX uq_payments_idempotency ON payment.payments (idempotency_key);
CREATE UNIQUE INDEX uq_payments_pg_order_id ON payment.payments (pg_order_id);
CREATE UNIQUE INDEX uq_payments_pg_payment_key ON payment.payments (pg_payment_key) WHERE pg_payment_key IS NOT NULL;
CREATE INDEX idx_payments_funding ON payment.payments (funding_id);
CREATE INDEX idx_payments_member ON payment.payments (member_id);
CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON payment.payments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- payment.payment_cancellations
-- 토스 취소 API(POST /v1/payments/{paymentKey}/cancel)는 취소 1건마다
-- 별도 transactionKey를 발급하며, 동일 paymentKey에 여러 번 부분취소가 가능하다.
-- refund.refund_requests(환불 "신청" 단위)와 별개로 실제 PG 취소 "실행" 이력을
-- 남겨 정산 배치(settlement_batches.refund_deduction_amount) 집계 근거로 삼는다.
-- ============================================================
CREATE TABLE payment.payment_cancellations (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id         UUID NOT NULL,
    refund_request_id  BIGINT,                    -- refund.refund_requests 참조, FK 아님(스키마는 같지만 참조 방향을 단방향으로 유지)
    pg_transaction_key VARCHAR(200) NOT NULL,      -- 토스 취소 건 transactionKey
    cancel_amount      BIGINT NOT NULL CHECK (cancel_amount >= 0),
    cancel_reason      VARCHAR(200) NOT NULL,
    canceled_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_cancellations_payment FOREIGN KEY (payment_id) REFERENCES payment.payments(id)
);
CREATE UNIQUE INDEX uq_payment_cancellations_pg_tx ON payment.payment_cancellations (pg_transaction_key);
CREATE INDEX idx_payment_cancellations_payment ON payment.payment_cancellations (payment_id);
CREATE INDEX idx_payment_cancellations_refund_request ON payment.payment_cancellations (refund_request_id);

-- ============================================================
-- payment.payment_event_outbox
-- order-service의 funding_event_outbox와 동일한 트랜잭셔널 아웃박스 패턴.
-- 발행 이벤트 2종(PaymentCompleted/RefundCompleted) — payload는 order-service
-- PaymentEventListener 계약(couponIssuanceId 포함)을 그대로 반영한다.
-- ============================================================
CREATE TABLE payment.payment_event_outbox (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type    VARCHAR(32) NOT NULL CHECK (event_type IN ('PaymentCompleted','RefundCompleted')),
    payment_id    UUID NOT NULL,
    funding_id    BIGINT NOT NULL,               -- order-service 참조, FK 아님
    payload       JSONB NOT NULL,                -- PaymentCompleted: {couponIssuanceId, paidAt} / RefundCompleted: {couponIssuanceId, refundReason, fullRefund}
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at  TIMESTAMP,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error    TEXT
);
CREATE INDEX idx_payment_event_outbox_unpublished
    ON payment.payment_event_outbox (id) WHERE published_at IS NULL;
CREATE INDEX idx_payment_event_outbox_payment ON payment.payment_event_outbox (payment_id);

-- ============================================================
-- payment.point_transactions [보류]
-- 요구사항정의서 13.2.1(정의)에만 "적립금"이 1회 언급되고 13.2.3/13.2.4(정책·기능정의)에는
-- 없어 MVP 포함 여부가 확정되지 않았다. 테이블만 스키마로 유지하고 애플리케이션 로직은
-- 연결하지 않는다 — PM 확인 필요[협의 필요].
-- ============================================================
CREATE TABLE payment.point_transactions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id     UUID NOT NULL,           -- member-service 참조, FK 아님
    point_change  BIGINT NOT NULL,
    point_balance BIGINT NOT NULL CHECK (point_balance >= 0),
    reason        VARCHAR(50) NOT NULL,
    funding_id    BIGINT,                  -- order-service 참조, FK 아님
    changed_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_point_transactions_member ON payment.point_transactions (member_id, changed_at);
CREATE INDEX idx_point_transactions_funding ON payment.point_transactions (funding_id);

-- ============================================================
-- refund.refund_requests
-- trigger_type별 처리 방식이 다르다. SIMPLE_CHANGE_OF_MIND(단순변심 취소),
-- GOAL_FAILED_AUTO(미달 자동환불)는 판매자/운영자 검토 없이 즉시 처리한다 — 요청 접수
-- 시점에 바로 status를 REQUESTED → PROCESSING → COMPLETED로 시스템이 자동 전이시키며,
-- UNDER_REVIEW/APPROVED 단계를 거치지 않는다. DEFECT(하자환불), SHIPPING_DELAY(발송지연
-- 취소)만 판매자 검토(UNDER_REVIEW→APPROVED/REJECTED) 단계를 거친다.
-- SYSTEM_RECONCILIATION은 PAYMENT-017(결제-재고만료 충돌) 전용 신규 유형[정책 확인 필요]
-- — SIMPLE_CHANGE_OF_MIND와 동일하게 즉시 처리한다.
-- alternate_refund_account — 원 결제수단 환불이 불가한 경우(카드 만료/해지 등) 참여자가
-- 입력하는 대체 계좌 정보(은행명/예금주/계좌번호). 금융정보이므로 애플리케이션 레벨
-- 암호화 적용(PAYMENT-005 예외처리 근거, security.md S9). [ERD 대비 변경] 타입을
-- JSONB → TEXT로 변경했다 — 암호화된 값은 더 이상 SQL JSON 연산자로 조회할 대상이
-- 아니므로(오히려 그게 안 되는 게 맞다), AttributeConverter가 만든 암호문 문자열을
-- 그대로 저장하는 게 실제 용도에 맞다.
-- is_full_refund — RefundCompleted 이벤트 payload(전액환불여부)의 근거 컬럼.
-- ============================================================
CREATE TABLE refund.refund_requests (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    funding_id                BIGINT NOT NULL,        -- order-service 참조, FK 아님
    payment_id                UUID NOT NULL,          -- 같은 데이터베이스의 다른 스키마 → FK 가능
    trigger_type              VARCHAR(30) NOT NULL
                              CHECK (trigger_type IN ('SIMPLE_CHANGE_OF_MIND','GOAL_FAILED_AUTO',
                                                       'DEFECT','SHIPPING_DELAY','SYSTEM_RECONCILIATION')),
    status                    VARCHAR(20) NOT NULL DEFAULT 'REQUESTED'
                              CHECK (status IN ('REQUESTED','UNDER_REVIEW','APPROVED',
                                                 'PROCESSING','COMPLETED','REJECTED')),
    is_full_refund             BOOLEAN,                -- COMPLETED 확정 시점에 채워짐(부분취소인 하자환불 대응)
    reason_detail             TEXT,
    evidence_urls              JSONB,
    rejected_reason           TEXT,
    alternate_refund_account  TEXT,
    requested_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at              TIMESTAMP,
    CONSTRAINT fk_refund_requests_payment FOREIGN KEY (payment_id) REFERENCES payment.payments(id)
);
CREATE INDEX idx_refund_requests_funding ON refund.refund_requests (funding_id);
CREATE INDEX idx_refund_requests_status ON refund.refund_requests (status);

-- ============================================================
-- settlement.settlement_holds
-- ============================================================
CREATE TABLE settlement.settlement_holds (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    funding_id  BIGINT NOT NULL,            -- order-service 참조, FK 아님
    payment_id  UUID NOT NULL,
    hold_amount BIGINT NOT NULL CHECK (hold_amount >= 0),
    status      VARCHAR(30) NOT NULL DEFAULT 'HOLDING'
               CHECK (status IN ('HOLDING','RELEASED_TO_SETTLEMENT','RELEASED_TO_REFUND')),
    released_at TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_settlement_holds_payment FOREIGN KEY (payment_id) REFERENCES payment.payments(id)
);
CREATE INDEX idx_settlement_holds_funding ON settlement.settlement_holds (funding_id);
CREATE UNIQUE INDEX uq_settlement_holds_payment ON settlement.settlement_holds (payment_id);

-- ============================================================
-- settlement.settlement_batches
-- status 컬럼: processed_at IS NULL 하나로만 "미처리"를 표현하면 PRD 9.1.3의
-- "이의신청 접수 시 해당 회차 지급 보류"를 구분할 방법이 없어 별도 상태값을 둔다.
-- 이의신청(settlement_disputes) 접수 시 ON_HOLD로 전환하고, 지급 완료 시 PAID로 전환한다.
-- ============================================================
CREATE TABLE settlement.settlement_batches (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id               UUID NOT NULL,   -- member-service 참조, FK 아님
    batch_type              VARCHAR(10) NOT NULL CHECK (batch_type IN ('INTERIM','FINAL')),
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','ON_HOLD','PAID')),
    period_start            TIMESTAMP NOT NULL,
    period_end              TIMESTAMP NOT NULL,
    gross_amount            BIGINT NOT NULL CHECK (gross_amount >= 0),
    platform_fee_amount     BIGINT NOT NULL CHECK (platform_fee_amount >= 0),
    refund_deduction_amount BIGINT NOT NULL DEFAULT 0 CHECK (refund_deduction_amount >= 0),
    coupon_deduction_amount BIGINT NOT NULL DEFAULT 0 CHECK (coupon_deduction_amount >= 0),
    total_amount            BIGINT NOT NULL CHECK (total_amount >= 0),
    processed_at            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_settlement_batches_seller ON settlement.settlement_batches (seller_id, batch_type);

CREATE TABLE settlement.settlement_batch_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id   BIGINT NOT NULL,
    funding_id BIGINT NOT NULL,      -- order-service 참조, FK 아님
    payment_id UUID NOT NULL,
    amount     BIGINT NOT NULL CHECK (amount >= 0),
    CONSTRAINT fk_settlement_batch_items_batch FOREIGN KEY (batch_id) REFERENCES settlement.settlement_batches(id),
    CONSTRAINT fk_settlement_batch_items_payment FOREIGN KEY (payment_id) REFERENCES payment.payments(id)
);
CREATE INDEX idx_settlement_batch_items_batch ON settlement.settlement_batch_items (batch_id);

CREATE TABLE settlement.settlement_disputes (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id      BIGINT NOT NULL,
    seller_id     UUID NOT NULL,     -- member-service 참조, FK 아님
    reason        TEXT NOT NULL,
    evidence_urls JSONB,
    status        VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'
                  CHECK (status IN ('RECEIVED','UNDER_REVIEW','COMPLETED')),
    requested_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at   TIMESTAMP,
    CONSTRAINT fk_settlement_disputes_batch FOREIGN KEY (batch_id) REFERENCES settlement.settlement_batches(id)
);
CREATE INDEX idx_settlement_disputes_batch ON settlement.settlement_disputes (batch_id);
CREATE INDEX idx_settlement_disputes_status ON settlement.settlement_disputes (status);

-- ============================================================
-- settlement.settlement_schedule [신규, ERD에 없음]
-- PAYMENT-013("달성확정일+5영업일" 실행 대상 등록) / PAYMENT-014(배송완료+14일)의 실행 대상을
-- 등록해두는 내부 큐 테이블 — payment-service CLAUDE.md가 두 가지 대안(내부 큐 테이블 vs
-- 배치 대상 판정 쿼리) 중 택 1을 지시해, order-service를 반복 동기 조회하지 않도록 큐 테이블을
-- 선택했다. FundingSucceeded 이벤트(ORDER-006) 수신 시 batch_type='INTERIM'으로, 배송완료
-- 통지 수신 시 batch_type='FINAL'로 등록한다(후자는 shipping-service가 아직 없어 이벤트
-- 소스가 없다 — PaymentFunctionalSpec.md PAYMENT-014 참고, 스케줄러는 골격만 존재).
-- ============================================================
CREATE TABLE settlement.settlement_schedule (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    funding_id   BIGINT NOT NULL,          -- order-service 참조, FK 아님
    project_id   BIGINT NOT NULL,          -- project-service 참조, FK 아님
    seller_id    UUID NOT NULL,            -- member-service 참조, FK 아님
    batch_type   VARCHAR(10) NOT NULL CHECK (batch_type IN ('INTERIM','FINAL')),
    due_at       TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_settlement_schedule_funding_type ON settlement.settlement_schedule (funding_id, batch_type);
CREATE INDEX idx_settlement_schedule_due ON settlement.settlement_schedule (due_at) WHERE processed_at IS NULL;
