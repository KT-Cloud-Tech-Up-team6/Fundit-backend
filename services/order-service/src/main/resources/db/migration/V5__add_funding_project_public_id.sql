-- cross-service ID(Long↔UUID) 통일 1단계(Expand) — project-service는 대외로 UUID(publicId)만
-- 노출하는데 fundings.project_id는 project-service 내부 Long PK를 그대로 들고 있었다.
-- 새 UUID 컬럼을 nullable로 추가만 한다. 기존 행은 애플리케이션의 1회성 백필 배치
-- (FundingProjectPublicIdBackfillRunner, 기본 비활성)가 채운다 — 서비스 간 DB 직접 접근 금지
-- 원칙 때문에 project-service의 기존 GET /internal/projects/{projectId}(Long) 내부 API를
-- 호출해서 값을 구한다.
ALTER TABLE fundings ADD COLUMN project_public_id UUID;
CREATE INDEX idx_fundings_project_public_id ON fundings (project_public_id);
