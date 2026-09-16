package com.fundit.member.infrastructure.persistence.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberEventOutboxJpaRepository extends JpaRepository<MemberEventOutboxJpaEntity, Long> {

    List<MemberEventOutboxJpaEntity> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
}
