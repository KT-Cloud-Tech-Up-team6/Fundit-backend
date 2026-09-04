package com.fundit.project.presentation.dto.reward;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Map;

/** 전부 선택 항목이다. isUnlimited는 재고 정책과 얽혀 있어 의도적으로 받지 않는다. */
public record RewardUpdateRequest(@Pattern(regexp = ".*\\S.*") String name,
                                  @Pattern(regexp = ".*\\S.*") String description,
                                  Long price,
                                  @JsonProperty("isEarlyBird") Boolean earlyBird,
                                  Boolean simpleRefundDisabled,
                                  @Pattern(regexp = ".*\\S.*") String categoryType,
                                  Map<String, Object> disclosure,
                                  @PositiveOrZero Integer displayOrder) {
}
