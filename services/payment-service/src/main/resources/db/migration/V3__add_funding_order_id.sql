-- cross-service ID 통일(#69) — payments/refund_requests가 order-service 내부 PK(BIGINT) 대신
-- 외부 노출 orderId(UUID, Funding.publicId)를 저장한다. settlement 테이블은 이번 범위 밖이라
-- BIGINT를 유지한다.
--
-- payment_event_outbox / notification_outbox의 funding_id도 같은 UUID를 실어 나르도록 바꾼다
-- (Payment.fundingId가 UUID가 되면 아웃박스 Java 매핑이 BIGINT와 맞지 않는다).
-- settlement_holds.funding_id는 BIGINT를 유지하되, 신규 결제는 Long PK를 더 이상 갖지 않아 NULL을 허용한다.

-- ============================================================
-- payment.payments
-- ============================================================
ALTER TABLE payment.payments
    ADD COLUMN funding_order_id UUID;

ALTER TABLE payment.payments
    ALTER COLUMN funding_id DROP NOT NULL;

DROP INDEX IF EXISTS payment.uq_payments_completed_funding;

ALTER TABLE payment.payments
    DROP COLUMN completed_funding_id;

ALTER TABLE payment.payments
    ADD COLUMN completed_funding_order_id UUID GENERATED ALWAYS AS (
        CASE WHEN status = 'COMPLETED' THEN funding_order_id ELSE NULL END
    ) STORED;

CREATE UNIQUE INDEX uq_payments_completed_funding_order
    ON payment.payments (completed_funding_order_id);

CREATE INDEX idx_payments_funding_order ON payment.payments (funding_order_id);

-- ============================================================
-- refund.refund_requests
-- ============================================================
ALTER TABLE refund.refund_requests
    ADD COLUMN funding_order_id UUID;

ALTER TABLE refund.refund_requests
    ALTER COLUMN funding_id DROP NOT NULL;

CREATE INDEX idx_refund_requests_funding_order ON refund.refund_requests (funding_order_id);

-- ============================================================
-- 아웃박스 — 도메인 UUID와 컬럼 타입을 맞춘다(기존 행이 있으면 funding_id 값은 버린다).
-- ============================================================
ALTER TABLE payment.payment_event_outbox DROP COLUMN funding_id;
ALTER TABLE payment.payment_event_outbox ADD COLUMN funding_id UUID NOT NULL;

ALTER TABLE notification_outbox DROP COLUMN funding_id;
ALTER TABLE notification_outbox ADD COLUMN funding_id UUID NOT NULL;

-- ============================================================
-- settlement_holds — 컬럼 타입(BIGINT)은 유지, 신규 결제용 NULL 허용만.
-- ============================================================
ALTER TABLE settlement.settlement_holds
    ALTER COLUMN funding_id DROP NOT NULL;
