-- ============================================================
-- LIVE검증 질문 문구/건수 연동(#155).
-- live-service가 방송 종료 후 발행하는 live.questions-summarized.v1을 받아 적재하는 로컬 복제본.
-- live-service DB를 직접 조회하지 않기 위한 테이블이고, 원본은 live_question_summaries(live-service) 쪽이다.
-- live_verifications는 "판매자 답변"만, 이 테이블은 "질문 문구/건수"만 갖는다 —
-- 같은 질문이 양쪽에 중복 저장되지 않도록 문구는 복제하지 않고 조회 시 조인한다.
-- ============================================================

CREATE TABLE live_question_summaries (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id           BIGINT NOT NULL,
    question_summary_id  VARCHAR(100) NOT NULL,
    summary_text         TEXT NOT NULL,
    question_count       INT NOT NULL DEFAULT 0,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_live_question_summaries_project FOREIGN KEY (project_id) REFERENCES projects(id)
);

-- Kafka는 at-least-once라 같은 이벤트가 두 번 온다. 멱등 기준은 (project_id, question_summary_id)로 잡고
-- 재수신 시 문구/건수만 갱신한다(eventId 기준으로 잡으면 요약이 갱신 발행될 때 반영을 놓친다).
CREATE UNIQUE INDEX uq_live_question_summaries_question
    ON live_question_summaries (project_id, question_summary_id);

CREATE TRIGGER trg_live_question_summaries_updated_at
    BEFORE UPDATE ON live_question_summaries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 이미 쌓인 중복 답변은 가장 최근 것만 남기고 소프트 삭제한다 — 안 하면 아래 유니크 인덱스 생성이 실패해
-- 서비스가 기동조차 못 한다. 하드 삭제가 아니라 deleted_at만 세팅하므로 필요하면 되살릴 수 있다.
UPDATE live_verifications v
   SET deleted_at = CURRENT_TIMESTAMP
 WHERE v.deleted_at IS NULL
   AND EXISTS (SELECT 1 FROM live_verifications o
                WHERE o.project_id = v.project_id
                  AND o.question_summary_id = v.question_summary_id
                  AND o.deleted_at IS NULL
                  AND (o.updated_at, o.id) > (v.updated_at, v.id));

-- 같은 질문에 답변 행이 여러 개 쌓이는 것을 DB에서도 막는다(애플리케이션 검증과 이중).
-- deleted_at IS NULL을 같이 걸어야 소프트 삭제 후 재등록이 가능하다(V17 멱등키 인덱스와 동일한 이유).
CREATE UNIQUE INDEX uq_live_verifications_question
    ON live_verifications (project_id, question_summary_id)
    WHERE deleted_at IS NULL;
