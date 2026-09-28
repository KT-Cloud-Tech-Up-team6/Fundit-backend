-- 프로젝트 참여자 수(FE BE-4). 펀딩 통계 이벤트(project.funding-reward-stats-updated.v1)에 실어 보내려고
-- 아웃박스에 값을 함께 저장한다. 기존 행은 0 — 이미 발행됐거나 다음 배치가 다시 계산한다.
ALTER TABLE funding_reward_stats_event_outbox ADD COLUMN participant_count INT NOT NULL DEFAULT 0;
