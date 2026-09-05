package com.fundit.project.infrastructure.persistence.liveverification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LiveVerificationJpaRepository extends JpaRepository<LiveVerificationJpaEntity, Long> {

    Optional<LiveVerificationJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    List<LiveVerificationJpaEntity> findByProjectIdAndDeletedAtIsNull(Long projectId);

    boolean existsByProjectIdAndDeletedAtIsNull(Long projectId);
}
