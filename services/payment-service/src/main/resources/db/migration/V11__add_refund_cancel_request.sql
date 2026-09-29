-- 토스 취소 호출을 DB 트랜잭션 밖으로 뺀다(#182). 예전에는 취소 API와 로컬 기록이 한 트랜잭션이라
-- 토스에선 취소됐는데 로컬 커밋이 실패하면 결제·환불 내역·RefundCompleted·정산 보류 해제가 전부
-- 롤백됐다(이미 환불된 금액이 판매자 정산에 들어갈 수 있음).
--
-- 이제 토스를 부르기 전에 "취소 요청됨"을 먼저 커밋한다 — status = 'PROCESSING' AND cancel_amount IS NOT NULL.
-- 교환의 PROCESSING(재발송 대기)은 cancel_amount가 NULL이라 구분된다. 확정 트랜잭션이 실패해도 이 행이
-- 남아 대사 배치가 토스 조회(cancels)로 결과를 맞춘다. V10과 같은 이유로 별도 작업 테이블을 두지 않는다.
--
-- cancel_amount/cancel_reason을 따로 두는 이유: 반품비 차감 부분취소 금액과 PG 취소 사유는 신청 사유
-- (reason_detail)와 다르고, 배치가 재시도할 때 정책값을 다시 계산하지 않고 요청 당시 값을 그대로 써야 한다.
-- cancel_requested_at은 배치가 "진행 중인 호출"을 건드리지 않도록 일정 시간 지난 행만 집는 기준이다
-- (하자환불 승인 건은 requested_at이 신청 시각이라 쓸 수 없다).

ALTER TABLE refund.refund_requests
    ADD COLUMN cancel_amount       BIGINT CHECK (cancel_amount >= 0),
    ADD COLUMN cancel_reason       VARCHAR(200),
    ADD COLUMN cancel_requested_at TIMESTAMP;

-- 결제당 진행 중인 취소 요청은 1건 — 이벤트 동시 수신으로 같은 결제를 두 번 취소 요청하는 것을 막는다.
-- 배치 스캔도 이 부분 인덱스 조건과 같다(진행 중인 행만 들어 있어 작다).
CREATE UNIQUE INDEX uq_refund_requests_cancel_in_flight
    ON refund.refund_requests (payment_id)
    WHERE status = 'PROCESSING' AND cancel_amount IS NOT NULL;
