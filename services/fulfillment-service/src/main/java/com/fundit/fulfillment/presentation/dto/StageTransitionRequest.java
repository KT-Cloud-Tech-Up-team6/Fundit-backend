package com.fundit.fulfillment.presentation.dto;

import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import jakarta.validation.constraints.NotNull;

/** FULFILLMENT-002 — API #2 요청. 화이트리스트 밖 값은 Jackson 역직렬화 단계에서 400으로 걸러진다. */
public record StageTransitionRequest(@NotNull FulfillmentStage stage) {
}
