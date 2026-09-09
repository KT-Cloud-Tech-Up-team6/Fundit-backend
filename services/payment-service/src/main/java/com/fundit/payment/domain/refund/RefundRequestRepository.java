package com.fundit.payment.domain.refund;

import java.util.Optional;

public interface RefundRequestRepository {

    RefundRequest save(RefundRequest refundRequest);

    Optional<RefundRequest> findById(Long id);
}
