-- FundingDeadlineWatcher가 project.funding-deadline-reached.v1을 중복 발행하지 않도록
-- 이미 통지한 프로젝트를 표시해두는 컬럼.
ALTER TABLE projects ADD COLUMN deadline_notified_at TIMESTAMP;

-- 펀딩 마감 도래 통지의 트랜잭셔널 아웃박스. 이벤트 종류가 하나뿐이라 event_type 컬럼을 두지
-- 않는다(reward_event_outbox와 달리 단일 목적) — 메시지 브로커가 아직 미확정이라 발행 어댑터는
-- 워커가 재시도 가능하도록 분리하고, 로깅만으로 발행 성공 처리하지 않는다.
CREATE TABLE funding_deadline_event_outbox (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id    BIGINT NOT NULL,
    goal_amount   BIGINT NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at  TIMESTAMP,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error    TEXT
);
CREATE INDEX idx_funding_deadline_event_outbox_unpublished
    ON funding_deadline_event_outbox (id) WHERE published_at IS NULL;
