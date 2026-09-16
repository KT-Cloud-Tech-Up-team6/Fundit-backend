package com.fundit.fulfillment.infrastructure.persistence.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FulfillmentDomainEventOutboxJpaRepository extends JpaRepository<FulfillmentDomainEventOutboxJpaEntity, Long> {

    List<FulfillmentDomainEventOutboxJpaEntity> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
}
