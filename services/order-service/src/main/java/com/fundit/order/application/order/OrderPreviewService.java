package com.fundit.order.application.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * ORDER-002(결제금액 계산)/ORDER-010(쿠폰 적용/해제) — 비영속(stateless) 계산 전용.
 * 호출해도 DB에 아무것도 생성되지 않는다(OrderDomainApiSpec.md #2 참고).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderPreviewService {

    private final OrderPricingService orderPricingService;

    public OrderPricingService.PricingResult preview(UUID memberId, Long projectId,
                                                       List<OrderLineItemRequest> lineItems,
                                                       List<String> couponCodes) {
        return orderPricingService.calculate(memberId, projectId, lineItems, couponCodes);
    }
}
