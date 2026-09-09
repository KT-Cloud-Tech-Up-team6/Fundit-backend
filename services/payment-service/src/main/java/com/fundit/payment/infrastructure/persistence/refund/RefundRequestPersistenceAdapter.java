package com.fundit.payment.infrastructure.persistence.refund;

import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

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
}
