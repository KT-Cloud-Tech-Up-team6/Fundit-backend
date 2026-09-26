-- 교환 재발송(payment-service 교환 승인/교환비 결제 후 내부 API 호출) — 같은 펀딩의 배송을
-- 새 사이클로 되돌린다. shipments는 펀딩당 1행(uq_shipments_funding_order)이라 행을 추가하지
-- 않고 상태를 PREPARING으로 리셋하며, 몇 번째 재발송인지와 어느 교환 신청 때문인지를 남긴다.
-- last_reshipment_refund_request_id는 같은 요청이 두 번 와도 다시 리셋하지 않기 위한 멱등키다.

ALTER TABLE shipments
    ADD COLUMN reshipment_count INT NOT NULL DEFAULT 0,
    ADD COLUMN last_reshipment_requested_at TIMESTAMPTZ,
    ADD COLUMN last_reshipment_refund_request_id BIGINT;  -- payment-service refund_requests 참조, FK 아님
