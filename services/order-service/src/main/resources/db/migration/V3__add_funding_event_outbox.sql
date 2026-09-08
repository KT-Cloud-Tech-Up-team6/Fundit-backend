-- ORDER-006/014가 발행하는 펀딩 이벤트(FundingGoalFailed/FundingSucceeded/FundingCancelledByMember)를
-- 최종적 일관성으로 전달하기 위한 트랜잭셔널 아웃박스. 메시지 브로커가 아직 미확정이라
-- project-service의 reward_event_outbox와 동일한 패턴을 그대로 따른다(CLAUDE.md 참고) —
-- 발행 어댑터는 워커가 재시도 가능하도록 분리하고, 로깅만으로 발행 성공 처리하지 않는다.
CREATE TABLE funding_event_outbox (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type    VARCHAR(32) NOT NULL,
    funding_id    BIGINT NOT NULL,
    project_id    BIGINT NOT NULL,
    member_id     UUID,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at  TIMESTAMP,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error    TEXT
);
CREATE INDEX idx_funding_event_outbox_unpublished
    ON funding_event_outbox (id) WHERE published_at IS NULL;
