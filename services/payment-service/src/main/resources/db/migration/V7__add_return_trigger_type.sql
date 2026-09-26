-- 환불 정책 V.1.0(2026-09-23) 「취소·반품·교환 공통 정책」 — 발송 후(수령 후) 구매자 귀책
-- 반품(단순변심·옵션 선택 오류)을 발송 전 취소와 구분해 저장하기 위해 trigger_type에
-- 'RETURN_CHANGE_OF_MIND'를 추가한다. 반품 승인 시에는 반품 배송비(5,000원)를 뺀 금액만
-- 부분취소되므로 is_full_refund=false로 기록된다.
--
-- [함께 수정] V1의 CHECK 제약에 'EXCHANGE'가 빠져 있었다(교환 신청 API는 이미 있는데 제약에는
-- 없어서 INSERT가 실패하는 상태였다 — 단위 테스트가 리포지토리를 모킹해 드러나지 않았다).
ALTER TABLE refund.refund_requests DROP CONSTRAINT refund_requests_trigger_type_check;

ALTER TABLE refund.refund_requests ADD CONSTRAINT refund_requests_trigger_type_check
    CHECK (trigger_type IN ('SIMPLE_CHANGE_OF_MIND', 'GOAL_FAILED_AUTO', 'DEFECT', 'SHIPPING_DELAY',
                            'SYSTEM_RECONCILIATION', 'EXCHANGE', 'RETURN_CHANGE_OF_MIND'));
