package com.fundit.payment.infrastructure.persistence.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentEventOutboxJpaRepository extends JpaRepository<PaymentEventOutboxJpaEntity, Long> {

    List<PaymentEventOutboxJpaEntity> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
}
