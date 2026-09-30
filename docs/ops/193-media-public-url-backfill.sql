-- #193 업로드 이미지 URL을 S3 직접 주소 → CDN 공개 주소로 치환하는 1회성 스크립트.
--
-- Flyway(V*.sql)에 넣지 않는다: 버킷명과 CDN 도메인이 환경(dev/prod)마다 달라
-- 마이그레이션 파일에 값을 박을 수 없다. 환경별로 아래 두 값만 바꿔 손으로 실행한다.
--
-- 실행 전 확인
--   1) 코드(media.public-base-url) 배포가 끝나 새 업로드는 이미 CDN 주소로 발급되고 있을 것
--   2) 인프라가 S3 직접 접근을 막기 "전"에 실행할 것 — 막힌 뒤에 돌리면 그 사이 이미지가 깨진다
--   3) 목업 이미지(https://infrastudy.store/media/mock/...)는 이미 새 형식이라 대상이 아니다
--      (LIKE 조건이 옛 S3 주소만 걸러내므로 자동으로 제외된다)
--
-- 환경별 치환 값 (dev 예시 — prod는 버킷/도메인을 맞춰 바꿀 것)
--   OLD: https://fundit-media-dev-team6.s3.ap-northeast-2.amazonaws.com/
--   NEW: https://infrastudy.store/media/
--
-- 실행 방법: 아래 \set 두 줄만 환경에 맞게 고친 뒤
--   psql -f docs/ops/193-media-public-url-backfill.sql

\set old 'https://fundit-media-dev-team6.s3.ap-northeast-2.amazonaws.com/'
\set new 'https://infrastudy.store/media/'

-- ────────────────────────────── project-service DB ──────────────────────────────
BEGIN;

-- 커버 이미지 (TEXT)
UPDATE projects
   SET cover_image_url = replace(cover_image_url, :'old', :'new')
 WHERE cover_image_url LIKE :'old' || '%';

-- 리워드 이미지 (TEXT)
UPDATE rewards
   SET image_url = replace(image_url, :'old', :'new')
 WHERE image_url LIKE :'old' || '%';

-- 스토리 블록 이미지 (JSONB 배열 안의 value) — 블록 구조를 따라 들어가는 대신 통째로
-- 문자열 치환한다. 대상 문자열이 URL뿐이라 구조를 재조립할 이유가 없다.
UPDATE projects
   SET intro_content = replace(intro_content::text, :'old', :'new')::jsonb
 WHERE intro_content::text LIKE '%' || :'old' || '%';

COMMIT;

-- ────────────────────────────── payment-service DB ──────────────────────────────
-- (별도 데이터베이스다 — 위 블록과 같은 세션에서 실행되지 않는다)
BEGIN;

-- 환불·반품 증빙 (JSONB 배열)
UPDATE refund.refund_requests
   SET evidence_urls = replace(evidence_urls::text, :'old', :'new')::jsonb
 WHERE evidence_urls::text LIKE '%' || :'old' || '%';

-- 정산 이의신청 증빙 (JSONB 배열)
UPDATE settlement.settlement_disputes
   SET evidence_urls = replace(evidence_urls::text, :'old', :'new')::jsonb
 WHERE evidence_urls::text LIKE '%' || :'old' || '%';

COMMIT;

-- 검증 — 아래 네 쿼리가 전부 0이어야 한다.
-- SELECT count(*) FROM projects WHERE cover_image_url LIKE :'old' || '%';
-- SELECT count(*) FROM rewards  WHERE image_url       LIKE :'old' || '%';
-- SELECT count(*) FROM projects WHERE intro_content::text LIKE '%' || :'old' || '%';
-- SELECT count(*) FROM refund.refund_requests WHERE evidence_urls::text LIKE '%' || :'old' || '%';
-- SELECT count(*) FROM settlement.settlement_disputes WHERE evidence_urls::text LIKE '%' || :'old' || '%';
