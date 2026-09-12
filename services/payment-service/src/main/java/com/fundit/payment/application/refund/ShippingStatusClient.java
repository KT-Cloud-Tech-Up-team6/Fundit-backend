package com.fundit.payment.application.refund;

/**
 * FS-096(발송지연 판정) 결과 확인용 아웃바운드 포트(PAYMENT-008). fulfillment-service의
 * 내부 API(FULFILLMENT-008, {@code GET /internal/fundings/{fundingId}/fulfillment-status})가
 * 아직 배선되지 않아 실제 구현체가 없다 — {@code StubShippingStatusClient}가 항상 "미발송"으로
 * 응답한다. fulfillment-service 연동 시 이 포트를 그 내부 API를 호출하는 Http 구현체로
 * 교체해야 한다.
 */
public interface ShippingStatusClient {

    boolean isAlreadyShipped(Long fundingId);
}
