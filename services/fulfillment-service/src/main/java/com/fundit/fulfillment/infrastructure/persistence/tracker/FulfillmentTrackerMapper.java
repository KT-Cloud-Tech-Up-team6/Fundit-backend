package com.fundit.fulfillment.infrastructure.persistence.tracker;

import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import org.springframework.stereotype.Component;

@Component
class FulfillmentTrackerMapper {

    FulfillmentTracker toDomain(FulfillmentTrackerJpaEntity entity) {
        return FulfillmentTracker.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .currentStage(FulfillmentStage.valueOf(entity.getCurrentStage()))
                .lastUpdatedAt(entity.getLastUpdatedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    FulfillmentTrackerJpaEntity toEntity(FulfillmentTracker domain) {
        return FulfillmentTrackerJpaEntity.builder()
                .id(domain.getId())
                .projectId(domain.getProjectId())
                .currentStage(domain.getCurrentStage().name())
                .lastUpdatedAt(domain.getLastUpdatedAt())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
