-- 발송 후 신청(하자환불·교환·반품)의 "주문당 미처리 신청 1건" 규칙을 DB에서 강제한다.
--
-- PostShipmentRefundRequestService가 신청 전에 exists 검사를 하지만, 검사와 INSERT 사이에
-- 다른 트랜잭션이 끼어들 수 있다(check-then-act). 반품 승인은 반품비를 뺀 금액을 부분취소하므로
-- 중복 접수가 두 건 승인되면 실제로 돈이 두 번 빠진다 — 응용 계층 검사만으로는 막을 수 없어
-- 유니크 인덱스를 최종 방어선으로 둔다.
--
-- COMPLETED/REJECTED는 조건에서 빼 재신청을 허용한다(반려된 하자환불을 반품으로 다시 접수하는
-- 흐름이 정상 경로다). funding_order_id가 NULL인 과거 행은 Postgres에서 NULL끼리 중복으로
-- 보지 않으므로 영향받지 않는다.
--
-- CONCURRENTLY를 쓰지 않은 이유: Flyway가 마이그레이션을 트랜잭션으로 감싸 실행하므로
-- CONCURRENTLY가 불가하다. 현재 테이블 규모에서는 짧은 쓰기 잠금으로 충분하다.
CREATE UNIQUE INDEX uq_refund_requests_unresolved_post_shipment
    ON refund.refund_requests (funding_order_id)
    WHERE trigger_type IN ('DEFECT', 'EXCHANGE', 'RETURN_CHANGE_OF_MIND')
      AND status IN ('REQUESTED', 'UNDER_REVIEW', 'APPROVED', 'PROCESSING');
