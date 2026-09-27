-- shipment.shipped.v1 payload에 재발송 식별자를 실어 보내기 위한 컬럼. payment-service가
-- "이 발송이 그 교환의 재발송분인지"를 판정해야 교환을 완료 처리할 수 있다 — Kafka는
-- at-least-once라서 최초 발송 이벤트가 나중에 재전달되면 식별자 없이는 구분할 수 없다.
-- 전이 시점의 값을 그대로 남기려고 아웃박스 행에 저장한다(발행 시점에 shipments를 다시 읽지 않음).

ALTER TABLE fulfillment_domain_event_outbox
    ADD COLUMN reshipment_refund_request_id BIGINT;  -- payment-service refund_requests.id 참조, FK 아님
