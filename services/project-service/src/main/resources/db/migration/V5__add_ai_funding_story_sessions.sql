-- ============================================================
-- [갭 보완] 펀딩스토리 AI 세션(PROJECT-011, PROJECT-012) 저장용 테이블이 V1에 없었다.
-- 외부 AI 서비스 연동 전까지는 애플리케이션 내 목(mock) 생성기가 즉시 결과를 채우지만,
-- 스키마/응답 계약은 실제 비동기 연동을 그대로 수용할 수 있게 설계한다.
-- ============================================================

CREATE TABLE ai_funding_story_sessions (
    id                   UUID PRIMARY KEY, -- 세션ID로 그대로 노출(서버 생성 UUID v7)
    project_id           BIGINT NOT NULL,
    seller_id            UUID NOT NULL,     -- projects.seller_id와 동일해야 함(FK 아님, 소유권 검증용 비정규화)
    product_description  TEXT NOT NULL,
    product_image_urls   JSONB,
    answers              JSONB,             -- 질의응답 내역([{questionId, answer}])
    status               VARCHAR(20) NOT NULL DEFAULT 'GENERATING'
        CHECK (status IN ('GENERATING','COMPLETED','FAILED')),
    additional_questions JSONB,
    result               JSONB,             -- {sections, imagesSource, warnings}
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_funding_story_sessions_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
CREATE INDEX idx_ai_funding_story_sessions_project ON ai_funding_story_sessions (project_id);
CREATE TRIGGER trg_ai_funding_story_sessions_updated_at
    BEFORE UPDATE ON ai_funding_story_sessions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
