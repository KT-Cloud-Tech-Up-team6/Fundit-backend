package com.fundit.order.domain.funding;

public record ShippingAddress(
        String recipientName,
        String phoneNumber,
        String zipcode,
        String addressLine1,
        String addressLine2
) {
}
