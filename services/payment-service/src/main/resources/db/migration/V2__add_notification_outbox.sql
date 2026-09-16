-- notification.raised.v1 발행용 트랜잭셔널 아웃박스(REFUND_STATUS). "완성된 문구"가 아니라
-- 도메인 식별자만 적재하고, Kafka 전송 시점에 transport가 조립한다(payment_event_outbox와 동일 원칙).
CREATE TABLE notification_outbox (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id      UUID NOT NULL,
    funding_id     BIGINT NOT NULL,
    status         VARCHAR(30) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at   TIMESTAMPTZ,
    attempt_count  INT NOT NULL DEFAULT 0,
    last_error     TEXT
);
CREATE INDEX idx_notification_outbox_unpublished
    ON notification_outbox (id) WHERE published_at IS NULL;
