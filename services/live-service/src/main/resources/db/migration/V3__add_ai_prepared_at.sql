-- 판매자 화면의 aiStatus(READY/PREPARING) 판단 근거.
-- AI의 GET /ready를 부르지 않는다 — 색인을 건 주체가 우리라 성공 시점을 우리가 알고 있고,
-- 매 화면 조회마다 AI를 한 번 더 왕복할 이유가 없다.
-- NULL이면 아직 색인 전(PREPARING)이라 "AI 준비 중"과 "모인 질문 없음"이 구분된다
-- (요구사항정의서 6.4.4.4 — 두 상태를 같은 문구로 띄우면 안 된다).
ALTER TABLE live_sessions
    ADD COLUMN ai_prepared_at TIMESTAMPTZ;
