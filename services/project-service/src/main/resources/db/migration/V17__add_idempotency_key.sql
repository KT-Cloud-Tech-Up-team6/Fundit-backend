-- 프로젝트/리워드 생성 멱등 키 (order-service V9__add_funding_idempotency_key.sql과 동일 패턴 —
-- 별도 테이블/TTL 없이 소유 레코드에 직접 저장, 부분 유니크 인덱스로 동시 요청 경합까지 방지).
ALTER TABLE projects ADD COLUMN idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX uq_projects_seller_idempotency_key
    ON projects (seller_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 리워드는 요청 본문이 있어 같은 키에 다른 내용이 오는 것을 구분하기 위한 해시도 함께 저장한다.
ALTER TABLE rewards ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE rewards ADD COLUMN idempotency_request_hash VARCHAR(64);

CREATE UNIQUE INDEX uq_rewards_project_idempotency_key
    ON rewards (project_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
