-- 교환 재발송 요청의 내구성 — 교환비 결제는 이미 승인됐는데 fulfillment 재발송 호출이 실패하면
-- 돈은 받고 재발송은 안 된 상태로 남는다. 요청 성공 시각을 결제와 같은 트랜잭션에서 비워둔 채
-- 커밋하고(PROCESSING + NULL = "재발송 요청이 아직 안 됨"), 워커가 이 행을 다시 집어 재요청한다.
-- 별도 아웃박스 테이블을 두지 않은 이유: 이 행 자체가 이미 작업 단위이고, fulfillment 쪽이
-- refund_request_id로 멱등이라 재요청이 안전하다.

ALTER TABLE refund.refund_requests
    ADD COLUMN reshipment_requested_at TIMESTAMP;

-- 워커가 매 주기에 훑는 조건(교환 + 재발송 미요청)만 인덱싱한다 — 대부분의 행은 대상이 아니다.
CREATE INDEX idx_refund_requests_pending_reshipment
    ON refund.refund_requests (id)
    WHERE trigger_type = 'EXCHANGE' AND status = 'PROCESSING' AND reshipment_requested_at IS NULL;
