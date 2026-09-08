package com.fundit.order.presentation.dto;

import com.fundit.order.domain.funding.ShippingAddress;
import jakarta.validation.constraints.NotBlank;

public record ShippingAddressRequest(
        @NotBlank String recipientName,
        @NotBlank String phoneNumber,
        @NotBlank String zipcode,
        @NotBlank String addressLine1,
        String addressLine2
) {

    public ShippingAddress toDomain() {
        return new ShippingAddress(recipientName, phoneNumber, zipcode, addressLine1, addressLine2);
    }
}
