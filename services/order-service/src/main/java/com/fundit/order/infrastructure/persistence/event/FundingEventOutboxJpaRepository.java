package com.fundit.order.infrastructure.persistence.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundingEventOutboxJpaRepository extends JpaRepository<FundingEventOutboxJpaEntity, Long> {

    List<FundingEventOutboxJpaEntity> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
}
