-- AI 실계약(v1, 2026-09-17 E2E 검증 완료) 반영. FAQ 클러스터링(유사질문 병합·3분 윈도우 승격)은
-- 전부 AI가 하고 live-service는 그 결과를 저장·노출만 한다 — 우리가 직접 topic으로
-- GROUP BY하던 집계 로직은 애플리케이션 레벨에서 걷어낸다.
--
-- ai_question_id는 AI의 FAQ 클러스터 id(qid, 예 fq_0002)다. 댓글 분류 응답의 question_id(q_0001)와는
-- 다른 값이다 — 그건 클러스터로 묶이기 전 개별 분류 결과라 우리가 저장할 필요가 없다.

ALTER TABLE live_question_summaries
    ADD COLUMN ai_question_id VARCHAR(50),  -- AI qid. 기존 행엔 없어 NULL 허용, 신규 행부터 채운다
    ADD COLUMN handled_by     VARCHAR(20),  -- PRODUCT / PLATFORM / UNANSWERABLE (AI가 분류한 사유)
    ADD COLUMN answered_by    VARCHAR(10),  -- SELLER / AI / NONE (누가 답했는지 — handled_by와 별개 축)
    ADD COLUMN promoted       BOOLEAN NOT NULL DEFAULT FALSE;  -- 3분 윈도우 TOP3 승격 여부

-- 세션 안에서 같은 AI 클러스터를 두 번 만들지 않는다. NULL은 유니크 제약에서 서로 다른 값
-- 취급이라(Postgres 기본) 과거 행(ai_question_id NULL)끼리는 충돌하지 않는다.
CREATE UNIQUE INDEX uq_live_question_summaries_ai_question
    ON live_question_summaries (session_id, ai_question_id);

-- 방송 중 채팅 → AI 배치 전송 스케줄러가 "아직 안 보낸 채팅"을 골라내는 기준.
-- question_summary_id(어느 대표질문으로 묶였는지)는 더 이상 이 경로에서 채우지 않는다 —
-- AI의 GET /faq/{qid}/comments가 클러스터별 원본 댓글을 직접 돌려주므로 로컬에서
-- FK로 연결해 둘 필요가 없다(컬럼 자체는 과거 데이터 호환을 위해 남겨둔다).
ALTER TABLE chat_messages
    ADD COLUMN sent_to_ai_at TIMESTAMPTZ;

CREATE INDEX idx_chat_messages_pending_ai
    ON chat_messages (session_id, sent_at) WHERE sent_to_ai_at IS NULL;
