-- 방송 중 화면의 주문 건수·매출 집계(GET /api/v1/orders/live-stats)용 인덱스.
-- 3~5초 폴링 × fundings 풀스캔은 사고라서 붙인다. coupons에는 V1부터 같은 인덱스가 있었고
-- fundings에만 빠져 있었다.
-- 부분 인덱스인 이유: 라이브 주문은 전체 주문의 일부라 NULL 행을 인덱스에 넣을 이유가 없다.
CREATE INDEX idx_fundings_live_session ON fundings (live_session_id) WHERE live_session_id IS NOT NULL;
