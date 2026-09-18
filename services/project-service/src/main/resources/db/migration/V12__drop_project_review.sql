-- 관리자 심사(PROJECT-030) 폐지 — 필수 항목이 채워지면 관리자 승인 없이 바로 ONGOING(공개)로
-- 전환한다. PENDING_REVIEW 상태와 그 이력을 담던 테이블은 더 이상 쓰지 않는다.
DROP TABLE project_review_requests;

ALTER TABLE projects
    DROP CONSTRAINT projects_status_check,
    ADD CONSTRAINT projects_status_check CHECK (status IN ('DRAFT', 'ONGOING', 'SUCCEEDED', 'FAILED'));
