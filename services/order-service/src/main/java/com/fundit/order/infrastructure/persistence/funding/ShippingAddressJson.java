package com.fundit.order.infrastructure.persistence.funding;

/** fundings.shipping_address(JSONB) 저장 포맷. project-service ProjectJpaEntity의 JSONB 매핑과 동일 패턴. */
record ShippingAddressJson(
        String recipientName,
        String phoneNumber,
        String zipcode,
        String addressLine1,
        String addressLine2
) {
}
