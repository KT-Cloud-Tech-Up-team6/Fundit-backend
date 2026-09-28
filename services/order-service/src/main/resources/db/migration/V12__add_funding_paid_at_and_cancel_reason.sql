-- 펀딩 내역 보강(FE 요청) 2건:
-- 1) paid_at — payment-service payment.completed.v1의 결제 완료 시각. 이 값은 payment-service
--    소관이라 order-service가 계산할 수 없고, 이벤트로 받아 저장한다. 이 컬럼이 생기기 전에
--    결제된 주문은 NULL로 남는다(이벤트를 재발행하지 않는다).
-- 2) cancel_reason / cancel_reason_detail — 참여 취소(ORDER-014) 시 구매자가 고른 사유.
--    본문 없이 취소하는 기존 클라이언트를 계속 받아야 해서 둘 다 NULL 허용이다.
ALTER TABLE fundings ADD COLUMN paid_at TIMESTAMPTZ;
ALTER TABLE fundings ADD COLUMN cancel_reason VARCHAR(30);
ALTER TABLE fundings ADD COLUMN cancel_reason_detail VARCHAR(100);
