package com.fundit.member.presentation.dto;

public record AddressListItemResponse(
        Long id, String recipientName, String phoneNumber, String zipcode,
        String addressLine1, String addressLine2, boolean isDefault
) {
}
