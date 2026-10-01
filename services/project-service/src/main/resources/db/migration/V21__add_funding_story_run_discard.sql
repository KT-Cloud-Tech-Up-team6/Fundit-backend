-- 생성 중 창을 닫고 폐기를 선택한 AI 스토리 run은 완료 callback이 와도 스토리에 반영하지 않는다(#226, QA-189).
-- AI 서버 작업 자체는 취소할 수 없어, run을 DISCARDED로 두고 callback에서 반영만 건너뛴다.

ALTER TABLE ai_funding_story_sessions ADD COLUMN idempotency_key VARCHAR(100);

ALTER TABLE ai_funding_story_sessions
    DROP CONSTRAINT ai_funding_story_sessions_status_check;

ALTER TABLE ai_funding_story_sessions
    ADD CONSTRAINT ai_funding_story_sessions_status_check
        CHECK (status IN ('GENERATING', 'COMPLETED', 'FAILED', 'DISCARDED'));

-- run ID를 받기 전에 닫은 경우 FE가 가진 식별자는 idempotency key뿐이다 — 키로 run을 찾는 경로이자,
-- 폐기 선점 행과 run 추적자가 같은 키를 동시에 점유하는 것을 막는 제약(V17과 같은 부분 유니크 패턴).
CREATE UNIQUE INDEX uq_ai_funding_story_sessions_project_idempotency_key
    ON ai_funding_story_sessions (project_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
