-- 찜 등록/해제 이벤트의 트랜잭셔널 아웃박스(MEMBER-005).
-- project-service reward_event_outbox(V7)와 같은 형태다.
--
-- 찜 쓰기와 같은 트랜잭션에서 여기 적재하고, 실제 발행은 워커가 재시도한다.
-- 로깅만으로 발행 성공 처리하지 않는다 — 발행된 척 비워지면 통계가 영영 어긋난다.
--
-- id를 BIGINT IDENTITY로 두는 이유: eventId = "member:{outboxId}"의 근거이고,
-- 워커가 재발행해도 값이 변하지 않아 소비 측 멱등이 성립한다(event-convention.md 5번).
CREATE TABLE wish_event_outbox (
                                   id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                   event_type    VARCHAR(32) NOT NULL,   -- PROJECT_WISHED / PROJECT_UNWISHED
                                   member_id     UUID NOT NULL,
                                   project_id    BIGINT NOT NULL,
                                   created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   published_at  TIMESTAMPTZ,
                                   attempt_count INT NOT NULL DEFAULT 0,
                                   last_error    TEXT
);
CREATE INDEX idx_wish_event_outbox_unpublished ON wish_event_outbox (id) WHERE published_at IS NULL;
