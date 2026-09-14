package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.domain.schedulechange.ScheduleChangeReasonType;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * FULFILLMENT-005 — API #4 요청. {@code reasonType}이 {@code OTHER}일 때 {@code reasonDetail}
 * 필수 여부는 화이트리스트만으로 표현할 수 없는 조건부 규칙이라 서비스 계층에서 검증한다.
 */
public record ScheduleChangeRequest(@NotNull FulfillmentStage stage, @NotNull ScheduleChangeReasonType reasonType,
                                     String reasonDetail, @NotNull Instant newPlannedDate) {
}
