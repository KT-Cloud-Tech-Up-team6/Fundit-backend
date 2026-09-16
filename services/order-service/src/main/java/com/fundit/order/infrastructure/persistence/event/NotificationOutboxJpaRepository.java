package com.fundit.order.infrastructure.persistence.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationOutboxJpaRepository extends JpaRepository<NotificationOutboxJpaEntity, Long> {

    List<NotificationOutboxJpaEntity> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
}
