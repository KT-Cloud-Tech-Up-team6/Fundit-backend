package com.fundit.order.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.order.application.order.OrderQueryService;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
import com.fundit.order.domain.funding.FundingLineItemOption;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.funding.ShippingAddress;
import com.fundit.order.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SellerOrderController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class SellerOrderControllerTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @Test
    void 판매자_발송목록을_조회한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Funding funding = Funding.builder().id(1L).publicId(orderId).memberId(UUID.randomUUID())
                .projectId(projectId).status(FundingStatus.GOAL_ACHIEVED)
                .shippingAddress(new ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시", "101호"))
                .shippingFee(0L).paymentExpiresAt(Instant.now())
                .lineItems(List.of(new FundingLineItem(1L, 5L, "리워드", 2, 10_000L,
                        List.of(new FundingLineItemOption(1L, 10L, "색상", 100L, "블랙")))))
                .createdAt(Instant.now()).build();
        when(orderQueryService.listForSeller(eq(sellerId), eq(projectId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(funding)));

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/orders")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.content[0].lineItems[0].rewardId").value(5))
                .andExpect(jsonPath("$.content[0].lineItems[0].quantity").value(2))
                .andExpect(jsonPath("$.content[0].lineItems[0].options[0].optionValueId").value(100))
                .andExpect(jsonPath("$.content[0].shippingAddress.recipientName").value("홍길동"))
                .andExpect(jsonPath("$.content[0].shippedAt").doesNotExist());
    }

    @Test
    void 발송된_건은_발송목록에_shippedAt이_실린다() throws Exception {
        // given — fulfillment-service shipment.shipped.v1로 채워지는 캐시 컬럼(V10)을 그대로 노출한다.
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant shippedAt = Instant.parse("2026-09-23T00:00:00Z");
        Funding funding = Funding.builder().id(1L).publicId(UUID.randomUUID()).memberId(UUID.randomUUID())
                .projectId(projectId).status(FundingStatus.GOAL_ACHIEVED)
                .shippingAddress(new ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시", "101호"))
                .shippingFee(0L).paymentExpiresAt(Instant.now())
                .lineItems(List.of(new FundingLineItem(1L, 5L, "리워드", 2, 10_000L, List.of())))
                .shippedAt(shippedAt)
                .createdAt(Instant.now()).build();
        when(orderQueryService.listForSeller(eq(sellerId), eq(projectId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(funding)));

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/orders")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].shippedAt").value("2026-09-23T00:00:00Z"));
    }
}
