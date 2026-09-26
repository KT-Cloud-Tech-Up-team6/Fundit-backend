-- 교환 배송비 수납(구매자 귀책 교환) — 같은 펀딩에 리워드 결제와 별개로 교환비 결제가 생긴다.
-- 기존 uq_payments_completed_funding_order(V3)는 "펀딩당 완료 결제 1건"이라 교환비 결제를 막으므로,
-- 용도(purpose)를 구분해 리워드 결제 1건 + 교환 신청당 교환비 결제 1건을 각각 강제한다.

ALTER TABLE payment.payments
    ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'REWARD'
        CHECK (purpose IN ('REWARD', 'EXCHANGE_FEE')),
    ADD COLUMN refund_request_id BIGINT;  -- refund.refund_requests 참조, FK 아님(참조 방향 단방향 유지)

-- 교환비 결제는 어떤 교환 신청의 비용인지 반드시 알아야 한다(재발송 트리거 대상 식별).
ALTER TABLE payment.payments
    ADD CONSTRAINT ck_payments_exchange_fee_refund_request
        CHECK (purpose <> 'EXCHANGE_FEE' OR refund_request_id IS NOT NULL);

-- 리워드 결제는 펀딩당 1건(V3 제약을 purpose 조건부로 옮긴 것), 교환비 결제는 교환 신청당 1건.
DROP INDEX payment.uq_payments_completed_funding_order;
CREATE UNIQUE INDEX uq_payments_completed_reward_funding_order
    ON payment.payments (completed_funding_order_id) WHERE purpose = 'REWARD';
CREATE UNIQUE INDEX uq_payments_completed_exchange_fee
    ON payment.payments (refund_request_id) WHERE purpose = 'EXCHANGE_FEE' AND status = 'COMPLETED';
CREATE INDEX idx_payments_refund_request ON payment.payments (refund_request_id)
    WHERE refund_request_id IS NOT NULL;
