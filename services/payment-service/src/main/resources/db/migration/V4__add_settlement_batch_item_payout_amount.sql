-- PAYMENT-013/014 — 정산 항목별 실지급액. INTERIM은 순액의 70%, FINAL은
-- 순액에서 동일 펀딩의 기지급 INTERIM 누계를 뺀 잔액을 담는다.
ALTER TABLE settlement.settlement_batch_items
    ADD COLUMN payout_amount BIGINT NOT NULL DEFAULT 0 CHECK (payout_amount >= 0);
