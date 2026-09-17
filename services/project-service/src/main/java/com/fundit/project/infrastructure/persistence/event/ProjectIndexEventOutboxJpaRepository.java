package com.fundit.project.infrastructure.persistence.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectIndexEventOutboxJpaRepository extends JpaRepository<ProjectIndexEventOutboxJpaEntity, Long> {

    List<ProjectIndexEventOutboxJpaEntity> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
}
