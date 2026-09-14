package com.fundit.fulfillment.infrastructure.persistence.tracker;

import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FulfillmentTrackerPersistenceAdapter implements FulfillmentTrackerRepository {

    private final FulfillmentTrackerJpaRepository jpaRepository;
    private final FulfillmentTrackerMapper mapper;

    @Override
    public boolean existsByProjectId(Long projectId) {
        return jpaRepository.existsByProjectId(projectId);
    }

    @Override
    public FulfillmentTracker save(FulfillmentTracker tracker) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(tracker)));
    }

    @Override
    public Optional<FulfillmentTracker> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId).map(mapper::toDomain);
    }

    @Override
    public List<FulfillmentTracker> findStale(Instant threshold) {
        return jpaRepository.findStale(threshold).stream().map(mapper::toDomain).toList();
    }
}
