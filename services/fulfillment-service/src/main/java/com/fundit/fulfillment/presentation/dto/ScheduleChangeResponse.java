package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.domain.schedulechange.ScheduleChangeReasonType;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaEntity;

import java.time.Instant;

public record ScheduleChangeResponse(Long scheduleChangeId, FulfillmentStage stage,
                                      ScheduleChangeReasonType reasonType, String reasonDetail,
                                      Instant oldPlannedDate, Instant newPlannedDate, Instant changedAt) {

    public static ScheduleChangeResponse from(FulfillmentScheduleChangeJpaEntity entity) {
        return new ScheduleChangeResponse(entity.getId(), FulfillmentStage.valueOf(entity.getStage()),
                ScheduleChangeReasonType.valueOf(entity.getReasonType()), entity.getReasonDetail(),
                entity.getOldPlannedDate(), entity.getNewPlannedDate(), entity.getChangedAt());
    }
}
