-- 판매자 발송목록 발송상태 필터·건수(#129) — fulfillment-service shipment.shipped.v1 이벤트를
-- 구독해 채우는 캐시 컬럼. NULL이면 발송 대기, 값이 있으면 발송 완료.
ALTER TABLE fundings ADD COLUMN shipped_at TIMESTAMPTZ;
