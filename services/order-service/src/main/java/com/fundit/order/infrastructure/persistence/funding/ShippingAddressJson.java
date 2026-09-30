package com.fundit.order.infrastructure.persistence.funding;

/**
 * fundings.shipping_address(JSONB) 저장 포맷. project-service ProjectJpaEntity의 JSONB 매핑과 동일 패턴.
 * public인 이유: dev 시연 시더({@code DemoFundingSeeder})가 다른 패키지에서 엔티티를 직접 만든다.
 */
public record ShippingAddressJson(
        String recipientName,
        String phoneNumber,
        String zipcode,
        String addressLine1,
        String addressLine2
) {
}
