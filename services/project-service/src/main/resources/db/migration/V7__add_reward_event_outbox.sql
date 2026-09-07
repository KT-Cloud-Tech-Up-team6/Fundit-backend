-- 리워드 수량 생성/변경을 order-service inventories에 최종적 일관성으로 동기화하기 위한
-- 트랜잭셔널 아웃박스. 메시지 브로커는 아직 미확정이라(ci-workflow-guide) 발행 어댑터는
-- 워커가 재시도 가능하도록 분리하고, 로깅만으로 발행 성공 처리하지 않는다.
CREATE TABLE reward_event_outbox (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type    VARCHAR(32) NOT NULL,
    reward_id     BIGINT NOT NULL,
    project_id    BIGINT NOT NULL,
    is_limited    BOOLEAN NOT NULL,
    quantity      INT,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at  TIMESTAMP,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error    TEXT
);
CREATE INDEX idx_reward_event_outbox_unpublished
    ON reward_event_outbox (id) WHERE published_at IS NULL;
