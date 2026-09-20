package com.fundit.order.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * ORDER-002/010 공용 요청 — POST /api/v1/orders/preview, POST /api/v1/orders 동일 바디.
 * projectId는 project-service의 publicId(UUID)를 그대로 받는다(cross-service ID 통일 #69) —
 * 클라이언트가 별도 해석 없이 프로젝트 조회 응답의 projectId를 그대로 넘기면 된다.
 */
public record OrderPreviewRequest(
        @NotNull UUID projectId,
        @NotEmpty @Valid List<OrderLineItemRequestDto> lineItems,
        @NotNull @Valid ShippingAddressRequest shippingAddress,
        List<String> couponCodes,
        /**
         * true면 couponCodes를 무시하고 보유 쿠폰 중 최적 조합을 서버가 자동 적용한다(ORDER-010).
         * 필드 생략 시 Jackson이 record의 primitive boolean에 null을 매핑하지 못해 400이 나므로
         * Boolean으로 두고, 사용부({@code autoApplyBestCoupon()})에서 null=false로 취급한다.
         */
        Boolean autoApplyBestCoupon
) {

    /** null(필드 생략)은 false로 취급한다 — record 컴포넌트 접근자와 이름이 겹치지 않게 별도 메서드로 둔다. */
    public boolean resolveAutoApplyBestCoupon() {
        return Boolean.TRUE.equals(autoApplyBestCoupon);
    }
}
