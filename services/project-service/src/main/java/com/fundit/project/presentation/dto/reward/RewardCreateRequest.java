package com.fundit.project.presentation.dto.reward;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record RewardCreateRequest(@NotBlank String name,
                                  String description,
                                  @NotNull Long price,
                                  @NotNull @JsonProperty("isUnlimited") Boolean unlimited,
                                  @JsonProperty("isEarlyBird") Boolean earlyBird,
                                  Boolean simpleRefundDisabled,
                                  String categoryType,
                                  Map<String, Object> disclosure) {

    public boolean earlyBirdOrDefault() {
        return Boolean.TRUE.equals(earlyBird);
    }

    public boolean simpleRefundDisabledOrDefault() {
        return Boolean.TRUE.equals(simpleRefundDisabled);
    }
}
