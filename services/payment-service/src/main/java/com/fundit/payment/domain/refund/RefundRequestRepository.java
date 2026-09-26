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
}
