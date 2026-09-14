-- FULFILLMENT-004(미등록 알림)/005(일정변경 알림)/010(자동확정 안내)이 발행하는 알림 이벤트를
-- 최종적 일관성으로 전달하기 위한 트랜잭셔널 아웃박스. notification-service가 아직 없고
-- 메시지 브로커도 미확정이라 order-service의 funding_event_outbox와 동일한 패턴을 따른다
-- (fulfillment-service CLAUDE.md 참고) — 발행 어댑터는 워커가 재시도 가능하도록 분리하고,
-- 로깅만으로 발행 성공 처리하지 않는다.
CREATE TABLE fulfillment_event_outbox (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type       VARCHAR(32) NOT NULL,
    project_id       BIGINT,
    funding_id       BIGINT,
    stage            VARCHAR(20),
    reason_type      VARCHAR(20),
    new_planned_date TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at     TIMESTAMPTZ,
    attempt_count    INT NOT NULL DEFAULT 0,
    last_error       TEXT
);
CREATE INDEX idx_fulfillment_event_outbox_unpublished
    ON fulfillment_event_outbox (id) WHERE published_at IS NULL;
