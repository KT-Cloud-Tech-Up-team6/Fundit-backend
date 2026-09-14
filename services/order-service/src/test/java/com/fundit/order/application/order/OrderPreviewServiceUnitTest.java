package com.fundit.order.application.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPreviewServiceUnitTest {

    @Mock
    private OrderPricingService orderPricingService;

    @InjectMocks
    private OrderPreviewService orderPreviewService;

    @Test
    void 가격_계산을_그대로_위임한다() {
        // given
        UUID memberId = UUID.randomUUID();
        List<OrderLineItemRequest> lineItems = List.of(new OrderLineItemRequest(1L, 2, List.of()));
        List<String> couponCodes = List.of("WELCOME10");
        var pricing = new OrderPricingService.PricingResult(10_000L, 3_000L, 1_000L, 12_000L,
                List.of(), List.of(), List.of());
        when(orderPricingService.calculate(memberId, 123L, lineItems, couponCodes)).thenReturn(pricing);

        // when
        var result = orderPreviewService.preview(memberId, 123L, lineItems, couponCodes);

        // then
        assertThat(result).isEqualTo(pricing);
    }
}
