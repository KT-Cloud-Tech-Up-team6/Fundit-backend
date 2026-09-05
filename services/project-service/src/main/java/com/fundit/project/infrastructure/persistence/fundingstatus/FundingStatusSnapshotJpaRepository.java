package com.fundit.project.infrastructure.persistence.fundingstatus;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FundingStatusSnapshotJpaRepository extends JpaRepository<FundingStatusSnapshotJpaEntity, Long> {
}
