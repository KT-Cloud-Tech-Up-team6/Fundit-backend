-- 복수 쿠폰(플랫폼+메이커) 전체 지원 — 한 주문에 최대 2개까지 적용될 수 있는데 단일 컬럼이라
-- 첫 번째 쿠폰만 스냅샷됐다. JSONB 배열로 바꿔 전부 담는다.
ALTER TABLE payment.payments ADD COLUMN coupon_issuance_ids JSONB;
UPDATE payment.payments SET coupon_issuance_ids = CASE
    WHEN coupon_issuance_id IS NULL THEN '[]'::jsonb
    ELSE jsonb_build_array(coupon_issuance_id)
END;
ALTER TABLE payment.payments ALTER COLUMN coupon_issuance_ids SET NOT NULL;
ALTER TABLE payment.payments DROP COLUMN coupon_issuance_id;
