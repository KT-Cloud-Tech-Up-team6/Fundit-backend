package com.fundit.member.infrastructure.persistence.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WishEventOutboxJpaRepository extends JpaRepository<WishEventOutboxJpaEntity, Long> {

    List<WishEventOutboxJpaEntity> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
}
