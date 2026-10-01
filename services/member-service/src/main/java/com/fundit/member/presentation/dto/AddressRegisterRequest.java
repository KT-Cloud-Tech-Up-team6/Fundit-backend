package com.fundit.member.presentation.dto;

import com.fundit.member.application.member.MemberSignupService.AddressPayload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddressRegisterRequest(
        @NotBlank @Pattern(regexp = AddressPayload.RECIPIENT_NAME_PATTERN, message = AddressPayload.RECIPIENT_NAME_MESSAGE)
        String recipientName,
        @NotBlank @Pattern(regexp = AddressPayload.PHONE_PATTERN, message = AddressPayload.PHONE_MESSAGE)
        String phoneNumber,
        @NotBlank String zipcode,
        @NotBlank String addressLine1,
        String addressLine2,
        Boolean isDefault
) {
}
