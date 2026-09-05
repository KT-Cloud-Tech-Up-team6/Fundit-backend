package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NoticeCreateRequest(
        @NotBlank
        @Pattern(regexp = "REWARD_INFO|EVENT|PRODUCTION_UPDATE|SHIPPING_INFO|ACHIEVEMENT_RATE|EXCHANGE_REFUND|PAYMENT_INFO|FAQ",
                message = "noticeType이 올바르지 않습니다.")
        String noticeType,
        @NotBlank @Size(max = 100) String title,
        @NotBlank String content
) {
}
