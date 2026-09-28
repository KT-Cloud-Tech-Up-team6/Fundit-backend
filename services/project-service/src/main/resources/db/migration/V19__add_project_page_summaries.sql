-- ============================================================
-- 상세 페이지 AI 요약(WHAT·WHY) 생성 상태와 결과(#169·#170).
-- 프로젝트당 1행. 요약 입력(제목·카테고리·리워드·본문)이 바뀔 수 있는 쓰기마다 dirty_at만 올리고,
-- 워커(PageSummaryWorker)가 입력 해시를 비교해 바뀐 경우에만 AI에 새 source_revision을 요청한다.
--
-- dirty_at은 쓰기 경로가, handled_dirty_at은 워커가 쓴다 — 두 쪽이 같은 컬럼을 쓰지 않으므로 워커가
-- 처리하는 동안 들어온 수정도 dirty_at > handled_dirty_at으로 남아 다음 주기에 다시 잡힌다.
-- ============================================================

CREATE TABLE project_page_summaries (
    project_id        BIGINT PRIMARY KEY,
    dirty_at          TIMESTAMP NOT NULL,
    handled_dirty_at  TIMESTAMP,
    content_hash      VARCHAR(64),
    source_revision   INT NOT NULL DEFAULT 0,
    attempt           INT NOT NULL DEFAULT 0,
    run_id            UUID,
    -- NULL: 아직 요청 전 / REQUESTED: AI 처리 중(폴링) / RETRY_WAIT: 재시도 대기 / SUCCEEDED / FAILED
    status            VARCHAR(20),
    sections          JSONB,
    error_code        VARCHAR(100),
    retry_count       INT NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMP,
    requested_at      TIMESTAMP,
    completed_at      TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_page_summaries_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT chk_project_page_summaries_status
        CHECK (status IS NULL OR status IN ('REQUESTED', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED'))
);

CREATE TRIGGER trg_project_page_summaries_updated_at
    BEFORE UPDATE ON project_page_summaries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 이미 공개된 프로젝트도 요약을 만들도록 대상에 올린다.
INSERT INTO project_page_summaries (project_id, dirty_at)
SELECT id, CURRENT_TIMESTAMP
  FROM projects
 WHERE status IN ('ONGOING', 'SUCCEEDED', 'FAILED')
   AND deleted_at IS NULL;
