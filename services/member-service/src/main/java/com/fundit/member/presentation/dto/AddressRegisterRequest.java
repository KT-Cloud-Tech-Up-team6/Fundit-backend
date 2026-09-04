package com.fundit.member.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record AddressRegisterRequest(
        @NotBlank String recipientName,
        @NotBlank String phoneNumber,
        @NotBlank String zipcode,
        @NotBlank String addressLine1,
        String addressLine2,
        Boolean isDefault
) {
}
