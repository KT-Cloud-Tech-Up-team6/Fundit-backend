package com.fundit.order.presentation.dto;

import com.fundit.order.domain.funding.ShippingAddress;

public record ShippingAddressResponse(
        String recipientName, String phoneNumber, String zipcode, String addressLine1, String addressLine2
) {

    public static ShippingAddressResponse from(ShippingAddress address) {
        return new ShippingAddressResponse(address.recipientName(), address.phoneNumber(), address.zipcode(),
                address.addressLine1(), address.addressLine2());
    }
}
