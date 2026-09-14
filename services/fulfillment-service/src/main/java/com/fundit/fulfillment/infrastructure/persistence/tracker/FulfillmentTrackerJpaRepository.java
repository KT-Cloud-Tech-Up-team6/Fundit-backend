package com.fundit.fulfillment.infrastructure.persistence.tracker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FulfillmentTrackerJpaRepository extends JpaRepository<FulfillmentTrackerJpaEntity, Long> {

    boolean existsByProjectId(Long projectId);

    Optional<FulfillmentTrackerJpaEntity> findByProjectId(Long projectId);

    @Query("SELECT t FROM FulfillmentTrackerJpaEntity t WHERE t.currentStage <> 'DELIVERY' "
            + "AND (t.lastUpdatedAt IS NULL OR t.lastUpdatedAt < :threshold)")
    List<FulfillmentTrackerJpaEntity> findStale(@Param("threshold") Instant threshold);
}
