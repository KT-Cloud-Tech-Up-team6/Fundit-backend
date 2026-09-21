-- 판매자 환불 목록 조회(PAYMENT-003 seller 변형)용 — 하자환불(DEFECT) 신청 시점에
-- order-service 조회로 채운다. 그 외 트리거 유형(즉시 처리)은 판매자 검토 대상이 아니라 null로 둔다.
ALTER TABLE refund.refund_requests ADD COLUMN seller_id UUID;
CREATE INDEX idx_refund_requests_seller ON refund.refund_requests (seller_id);
