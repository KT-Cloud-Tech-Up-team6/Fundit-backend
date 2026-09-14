package com.fundit.fulfillment.infrastructure.persistence.schedulechange;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FulfillmentScheduleChangeJpaRepository extends JpaRepository<FulfillmentScheduleChangeJpaEntity, Long> {

    List<FulfillmentScheduleChangeJpaEntity> findByTrackerIdOrderByChangedAtDesc(Long trackerId);
}
