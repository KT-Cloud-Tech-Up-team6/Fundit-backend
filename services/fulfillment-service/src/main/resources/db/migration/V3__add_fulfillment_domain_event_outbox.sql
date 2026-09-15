-- shipping.completed.v1(PAYMENT-014 최종정산 트리거) 등 fulfillment-service가 발행하는 "도메인 사실"
-- 이벤트 전용 아웃박스. fulfillment_event_outbox(알림 명령, notification.raised.v1행)와 파이프를
-- 분리한다 — 이건 알림 문구가 아니라 다른 서비스(payment-service)가 소비하는 도메인 이벤트라
-- 목적이 다르다(order-service funding_event_outbox와 동일 성격).
CREATE TABLE fulfillment_domain_event_outbox (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type    VARCHAR(32) NOT NULL,
    funding_id    BIGINT NOT NULL,
    project_id    BIGINT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at  TIMESTAMPTZ,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error    TEXT
);
CREATE INDEX idx_fulfillment_domain_event_outbox_unpublished
    ON fulfillment_domain_event_outbox (id) WHERE published_at IS NULL;
