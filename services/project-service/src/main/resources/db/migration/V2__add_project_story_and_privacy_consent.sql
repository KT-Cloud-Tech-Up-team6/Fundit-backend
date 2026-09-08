-- ============================================================
-- [갭 보완] V1에는 프로젝트 소개 콘텐츠(PROJECT-006)와 개인정보 수집 동의 이력(PROJECT-005)을
-- 저장할 컬럼/테이블이 없었고, projects의 business_type/category_major/category_minor/
-- title/goal_amount가 전부 NOT NULL이라 "신규 프로젝트 생성"(PROJECT-003, 값 없이 DRAFT만
-- 생성 후 기본정보는 PATCH .../basic-info에서 이어서 채움)이 애초에 불가능했다.
-- ProjectDomainApiSpec.md #2·#4·#5·#8 구현을 위해 보완한다.
-- V1은 수정하지 않는다(루트 CLAUDE.md 규칙) — 신규 버전으로만 보완.
-- ============================================================

-- 신규 프로젝트는 seller_id/status(DRAFT)만 갖고 생성되고, 기본정보는 이후 PATCH로 채워진다.
-- 값이 채워졌는지(필수 작성 항목 완료 여부)는 애플리케이션(Project.hasCompletedBasicInfo())이
-- null 여부로 판단하므로, DB 레벨 NOT NULL은 그 전제와 맞지 않아 제거한다.
ALTER TABLE projects ALTER COLUMN business_type DROP NOT NULL;
ALTER TABLE projects ALTER COLUMN category_major DROP NOT NULL;
ALTER TABLE projects ALTER COLUMN category_minor DROP NOT NULL;
ALTER TABLE projects ALTER COLUMN title DROP NOT NULL;
ALTER TABLE projects ALTER COLUMN goal_amount DROP NOT NULL;

-- 프로젝트 소개 콘텐츠(PATCH .../story) — 대표이미지 1개, 텍스트/이미지/영상 블록 목록(JSONB)
ALTER TABLE projects ADD COLUMN cover_image_url TEXT;
ALTER TABLE projects ADD COLUMN intro_content JSONB;

-- 개인정보 수집 동의 이력(POST .../privacy-consent) — 법적 근거자료이므로 append-only,
-- UPDATE/DELETE API를 만들지 않는다(project-service CLAUDE.md 핵심 설계 결정과 동일한 원칙).
CREATE TABLE project_privacy_consents (
                                           id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                           project_id   BIGINT NOT NULL,
                                           agreed       BOOLEAN NOT NULL,
                                           consented_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           CONSTRAINT fk_project_privacy_consents_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
CREATE INDEX idx_project_privacy_consents_project ON project_privacy_consents (project_id, consented_at DESC);
