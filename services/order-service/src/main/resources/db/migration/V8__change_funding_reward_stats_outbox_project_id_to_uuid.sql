-- PROJECT-015 리워드 통계 아웃박스가 참조하던 fundings.project_id(레거시 Long)는
-- cross-service ID 통일(#69) 이후 신규 펀딩에는 채워지지 않아 항상 NULL이다 — 이 컬럼으로
-- 조회하던 배치가 실질적으로 아무 프로젝트도 찾지 못했다. project_public_id(UUID) 기준으로
-- 바꾼다. 아직 배포되지 않은 기능이라 기존 행 보존 없이 타입만 교체한다.
ALTER TABLE funding_reward_stats_event_outbox DROP COLUMN project_id;
ALTER TABLE funding_reward_stats_event_outbox ADD COLUMN project_id UUID NOT NULL;
