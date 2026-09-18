package com.fundit.project.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/** PATCH .../rewards/{id} — 등록과 동일한 필드 중 변경할 필드만 부분 전달한다. */
public record RewardUpdateRequest(
        String name,
        String description,
        String imageUrl,
        @PositiveOrZero Long price,
        Boolean isLimited,
        /** -1은 "무제한" sentinel — 서버가 isLimited:false + quantity:null로 정규화한다({@code RewardController}). */
        @Min(-1) Integer quantity,
        Boolean isEarlyBird,
        @Pattern(regexp = "AMOUNT|RATE", message = "earlyBirdDiscountType은 AMOUNT, RATE 중 하나여야 합니다.")
        String earlyBirdDiscountType,
        @PositiveOrZero Long earlyBirdDiscountValue,
        @Valid List<RewardOptionRequest> options
) {
}
