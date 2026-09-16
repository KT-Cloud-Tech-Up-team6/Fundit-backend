-- member-service가 발행하는 이벤트의 트랜잭셔널 아웃박스.
-- 서비스당 아웃박스 한 벌이 레포 관행이다(order funding_event_outbox가 event_type으로
-- 여러 이벤트를 섞고 member_id를 nullable로 두는 것과 같은 형태).
--
-- 도메인 쓰기와 같은 트랜잭션에서 여기 적재하고, 실제 발행은 워커가 재시도한다.
-- 로깅만으로 발행 성공 처리하지 않는다 — 발행된 척 비워지면 소비 측이 영영 어긋난다.
--
-- id를 BIGINT IDENTITY로 두는 이유: eventId = "member:{outboxId}"의 근거이고,
-- 워커가 재발행해도 값이 변하지 않아 소비 측 멱등이 성립한다(event-convention.md 5번).
CREATE TABLE member_event_outbox (
                                     id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                     event_type    VARCHAR(32) NOT NULL,   -- PROJECT_WISHED / PROJECT_UNWISHED / MEMBER_SIGNED_UP
                                     member_id     UUID NOT NULL,
                                     project_id    BIGINT,                 -- 찜 이벤트만 채운다. 가입 이벤트에는 없다
                                     created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     published_at  TIMESTAMPTZ,
                                     attempt_count INT NOT NULL DEFAULT 0,
                                     last_error    TEXT
);
CREATE INDEX idx_member_event_outbox_unpublished ON member_event_outbox (id) WHERE published_at IS NULL;
