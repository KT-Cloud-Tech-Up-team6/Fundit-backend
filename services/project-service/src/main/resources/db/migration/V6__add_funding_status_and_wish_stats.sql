-- ============================================================
-- [갭 보완] 펀딩 현황(PROJECT-015)·찜 통계(PROJECT-016) 조회용 읽기 모델 테이블이 V1에 없었다.
-- 둘 다 이 서비스가 원장을 갖지 않고 다른 서비스(order-service/member-service)가 발행하는
-- 이벤트를 구독해 동기화하는 스냅샷이다(project-service CLAUDE.md 핵심 설계 결정과 동일한 원칙
-- — 재고를 order-service 조회로 그대로 쓰는 것과 같은 이유). 메시지 브로커가 아직 구성되지 않아
-- 현재는 이벤트 구독자가 없고, 테이블/조회 API만 먼저 준비해둔다(값은 동기화 전까지 기본값 0).
-- open_notify_count는 이미 존재하는 project_open_notify_requests를 그대로 집계하므로 별도 컬럼을
-- 두지 않는다.
-- ============================================================

CREATE TABLE funding_status_snapshots (
    project_id        BIGINT PRIMARY KEY,
    current_amount    BIGINT NOT NULL DEFAULT 0,
    achievement_rate  INT NOT NULL DEFAULT 0,
    participant_count INT NOT NULL DEFAULT 0,
    reward_stats      JSONB, -- [{rewardId, purchasedQuantity}]
    last_synced_at    TIMESTAMP,
    CONSTRAINT fk_funding_status_snapshots_project FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE TABLE project_wish_stats (
    project_id  BIGINT PRIMARY KEY,
    wish_count  INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_project_wish_stats_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
