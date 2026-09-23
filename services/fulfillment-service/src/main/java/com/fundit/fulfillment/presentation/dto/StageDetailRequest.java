package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** FULFILLMENT-002 — API #3 요청. photoUrls는 media/upload-url로 발급받은 fileUrl만 담는다(선택, 최대 5장). */
public record StageDetailRequest(@NotNull FulfillmentStage stage, Instant plannedStartAt, Instant plannedEndAt,
                                  @NotBlank String detailText, @Size(max = 5) List<String> photoUrls) {
}
