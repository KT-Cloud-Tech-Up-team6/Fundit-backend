package com.fundit.payment.domain.refund;

import java.util.Optional;
import java.util.UUID;

public interface RefundRequestRepository {

    RefundRequest save(RefundRequest refundRequest);

    Optional<RefundRequest> findById(Long id);

    /**
     * 같은 주문에 아직 처리되지 않은 발송 후 신청(하자환불·교환·반품)이 있는지. 중복 접수를 막지
     * 않으면 반품비 차감 부분취소가 두 번 실행될 수 있다.
     */
    boolean existsUnresolvedPostShipmentRequest(UUID fundingId);

    /**
     * 재발송 요청까지 끝나 배송을 기다리는 교환 신청(PROCESSING). 재발송분의 배송완료 이벤트로
     * 교환을 종료하기 위해 쓴다 — 펀딩당 미처리 발송 후 신청은 1건이라 최대 1건이다.
     */
    Optional<RefundRequest> findReshippingExchangeByFundingId(UUID fundingId);
}
