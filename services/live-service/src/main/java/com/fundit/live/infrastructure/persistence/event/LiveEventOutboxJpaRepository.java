package com.fundit.live.infrastructure.persistence.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LiveEventOutboxJpaRepository extends JpaRepository<LiveEventOutboxJpaEntity, Long> {

    List<LiveEventOutboxJpaEntity> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
}
