-- 다시보기 챕터(MARKER) 자동 공개(PM-1 "생성되면 자동 공개", FE BE-23).
-- 코드 변경 전에 저장된 완료 마커는 비공개로 남아 있어 같이 공개한다. 클립은 그대로 판매자 확정 대상이다.
UPDATE live_highlights
SET is_public = TRUE
WHERE kind = 'MARKER'
  AND generation_status = 'COMPLETED'
  AND is_public = FALSE;
