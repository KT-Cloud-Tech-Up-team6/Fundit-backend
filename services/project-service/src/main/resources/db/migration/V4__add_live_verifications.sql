-- ============================================================
-- [갭 보완] LIVE검증 콘텐츠(PROJECT-014, PROJECT-019) 저장용 테이블이 V1에 없었다.
-- 방송 송출 자체는 live-service 소관이고, project-service는 방송 종료 후 남는
-- 질문요약 참조값(question_summary_id, FK 아님)과 판매자 답변만 보관한다.
-- ============================================================

CREATE TABLE live_verifications (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id           BIGINT NOT NULL,
    question_summary_id  VARCHAR(100) NOT NULL,
    question_count       INT NOT NULL DEFAULT 0, -- live-service 연동 전까지는 0(연동 시 채워짐)
    answer               TEXT NOT NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at           TIMESTAMP,
    CONSTRAINT fk_live_verifications_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
CREATE INDEX idx_live_verifications_project ON live_verifications (project_id);
CREATE TRIGGER trg_live_verifications_updated_at
    BEFORE UPDATE ON live_verifications
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
