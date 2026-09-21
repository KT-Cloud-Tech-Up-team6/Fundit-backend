-- PROJECT-015 — 리워드 단위 펀딩 구매 통계(project.funding-reward-stats-updated.v1)의
-- 트랜잭셔널 아웃박스. 프로젝트당 한 행에 리워드 통계 전체를 JSONB로 담아, project-service가
-- 구독 시 전체 교체(REPLACE) 방식으로 반영한다.
CREATE TABLE funding_reward_stats_event_outbox (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id    BIGINT NOT NULL,
    reward_stats  JSONB NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at  TIMESTAMP,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error    TEXT
);
CREATE INDEX idx_funding_reward_stats_event_outbox_unpublished
    ON funding_reward_stats_event_outbox (id) WHERE published_at IS NULL;
