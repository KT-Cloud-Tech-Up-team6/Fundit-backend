-- notification.raised.v1 발행용 트랜잭셔널 아웃박스(REWARD_RESTOCK/COUPON_EXPIRING).
-- 도메인별 outbox를 또 만들지 않고 알림 전용 outbox 하나로 통일한다 — 두 notifType 모두
-- "완성된 문구"가 아니라 도메인 식별자만 적재하고, Kafka 전송 시점에 transport가 조립한다
-- (funding_event_outbox와 동일 원칙).
CREATE TABLE notification_outbox (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    notif_type          VARCHAR(20) NOT NULL,
    member_id           UUID NOT NULL,
    reward_id           BIGINT,
    coupon_issuance_id  BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at        TIMESTAMPTZ,
    attempt_count       INT NOT NULL DEFAULT 0,
    last_error          TEXT
);
CREATE INDEX idx_notification_outbox_unpublished
    ON notification_outbox (id) WHERE published_at IS NULL;

-- 쿠폰 만료임박(COUPON_EXPIRING) 리마인더를 이미 보냈는지 표시 — 중복 알림 방지.
-- 실제 만료 처리(EXPIRED 전이)와는 별개 개념이라 기존 상태 컬럼을 재사용하지 않는다.
ALTER TABLE coupon_issuances ADD COLUMN expiring_notified_at TIMESTAMPTZ;
