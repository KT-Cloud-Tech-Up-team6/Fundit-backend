package com.fundit.payment.application.refund;

/**
 * FS-096(발송지연 판정) 결과 확인용 아웃바운드 포트(PAYMENT-008). shipping-service가 아직
 * 로드맵상의 예정 서비스라 실제 구현체가 없다 — {@code StubShippingStatusClient}가 항상
 * "미발송"으로 응답한다.
 */
public interface ShippingStatusClient {

    boolean isAlreadyShipped(Long fundingId);
}
