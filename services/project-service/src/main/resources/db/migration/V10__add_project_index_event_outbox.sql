-- 프로젝트 공개/수정을 search-service 색인(SEARCH-011)에 최종적 일관성으로 동기화하기 위한
-- 트랜잭셔널 아웃박스. reward_event_outbox(V7)와 동일한 패턴 — 발행은 워커가 재시도하고,
-- 로깅만으로 성공 처리하지 않는다.
CREATE TABLE project_index_event_outbox (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type           VARCHAR(32) NOT NULL,
    project_id           BIGINT NOT NULL,
    project_public_id    UUID NOT NULL,
    seller_id            UUID NOT NULL,
    seller_display_name  VARCHAR(50),
    title                VARCHAR(40) NOT NULL,
    thumbnail_url        VARCHAR(500),
    category_major       VARCHAR(50) NOT NULL,
    category_minor       VARCHAR(50) NOT NULL,
    goal_amount          BIGINT NOT NULL,
    funding_start_at     TIMESTAMP,
    funding_deadline     TIMESTAMP NOT NULL,
    project_created_at   TIMESTAMP NOT NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at         TIMESTAMP,
    attempt_count        INT NOT NULL DEFAULT 0,
    last_error           TEXT
);
CREATE INDEX idx_project_index_event_outbox_unpublished
    ON project_index_event_outbox (id) WHERE published_at IS NULL;
