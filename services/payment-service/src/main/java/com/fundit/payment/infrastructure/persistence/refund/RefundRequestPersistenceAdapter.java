package com.fundit.payment.infrastructure.persistence.refund;

import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import com.fundit.payment.domain.refund.RefundRequestStatus;
import com.fundit.payment.domain.refund.RefundTriggerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefundRequestPersistenceAdapter implements RefundRequestRepository {

    private final RefundRequestJpaRepository jpaRepository;
    private final RefundRequestMapper mapper;

    @Override
    public RefundRequest save(RefundRequest refundRequest) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(refundRequest)));
    }

    @Override
    public Optional<RefundRequest> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    /** 미처리 = 완료/반려 전(REQUESTED/UNDER_REVIEW/APPROVED/PROCESSING). */
    private static final List<String> UNRESOLVED_STATUSES = List.of(RefundRequestStatus.REQUESTED.name(),
            RefundRequestStatus.UNDER_REVIEW.name(), RefundRequestStatus.APPROVED.name(),
            RefundRequestStatus.PROCESSING.name());

    @Override
    public Optional<RefundRequest> findReshippingExchangeByFundingId(UUID fundingId) {
        return jpaRepository.findFirstByFundingOrderIdAndTriggerTypeAndStatusOrderByRequestedAtDesc(fundingId,
                        RefundTriggerType.EXCHANGE.name(), RefundRequestStatus.PROCESSING.name())
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsUnresolvedPostShipmentRequest(UUID fundingId) {
        return jpaRepository.existsByFundingOrderIdAndTriggerTypeInAndStatusIn(fundingId,
                RefundTriggerType.postShipmentTypes().stream().map(RefundTriggerType::name).toList(),
                UNRESOLVED_STATUSES);
    }
}
