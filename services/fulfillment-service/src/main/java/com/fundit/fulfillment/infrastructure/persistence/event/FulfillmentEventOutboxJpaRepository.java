package com.fundit.fulfillment.infrastructure.persistence.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FulfillmentEventOutboxJpaRepository extends JpaRepository<FulfillmentEventOutboxJpaEntity, Long> {

    List<FulfillmentEventOutboxJpaEntity> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
}
