-- ORDER-003 주문 생성 멱등 키. 요청 본문 해시(idempotency_request_hash)까지 같이 저장해
-- 같은 키로 다른 내용이 오면 CONFLICT로 거부한다(fundings 테이블에 직접 저장 — payment-service
-- payments.idempotency_key와 동일 패턴, 별도 테이블/TTL 없이 주문 레코드와 함께 영구 보관).
ALTER TABLE fundings ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE fundings ADD COLUMN idempotency_request_hash VARCHAR(64);

CREATE UNIQUE INDEX uq_fundings_member_idempotency_key
    ON fundings (member_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
