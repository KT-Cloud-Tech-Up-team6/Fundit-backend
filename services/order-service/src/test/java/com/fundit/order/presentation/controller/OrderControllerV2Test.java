package com.fundit.order.presentation.controller;

import com.fundit.order.application.order.OrderCreateService;
import com.fundit.order.application.order.OrderPreviewService;
import com.fundit.order.application.order.OrderPricingService;
import com.fundit.order.application.order.OrderQueryService;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.funding.ShippingAddress;
import com.fundit.common.webmvc.auth.CommonWebConfig;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * cross-service ID 통일(#69) — v2는 project-service 내부 해석 호출 없이 projectId(UUID)를
 * 그대로 받는다({@link OrderControllerTest}의 v1과 달리 ProjectOwnershipClient 목이 필요 없다).
 */
@WebMvcTest(OrderControllerV2.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class OrderControllerV2Test {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";
    private static final UUID PROJECT_ID = UUID.randomUUID();

    private final String requestBody = """
            {
              "projectId": "%s",
              "lineItems": [ { "rewardId": 1, "quantity": 1, "optionValueIds": [] } ],
              "shippingAddress": { "recipientName": "홍길동", "phoneNumber": "010-1234-5678",
                "zipcode": "12345", "addressLine1": "서울시", "addressLine2": "101동" }
            }
            """.formatted(PROJECT_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderPreviewService orderPreviewService;
    @MockitoBean
    private OrderCreateService orderCreateService;
    @MockitoBean
    private OrderQueryService orderQueryService;

    @Test
    void 미리보기_요청하면_projectId를_UUID_그대로_전달한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        var pricing = new OrderPricingService.PricingResult(10_000L, 3_000L, 0L, 13_000L, List.of(), List.of(), List.of());
        when(orderPreviewService.preview(eq(memberId), eq(PROJECT_ID), any(), any())).thenReturn(pricing);

        // when & then
        mockMvc.perform(post("/api/v2/orders/preview")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalAmount").value(13_000));
    }

    @Test
    void 주문을_생성하면_201을_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Funding funding = Funding.builder().id(1L).publicId(orderId).memberId(memberId).projectId(PROJECT_ID)
                .projectTitle("프로젝트").status(FundingStatus.PENDING)
                .shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(3_000L).paymentExpiresAt(Instant.now().plusSeconds(1800))
                .lineItems(List.of()).createdAt(Instant.now()).build();
        when(orderCreateService.create(eq(memberId), eq(PROJECT_ID), any(), any(), any()))
                .thenReturn(new OrderCreateService.OrderCreateResult(funding, 13_000L));

        // when & then
        mockMvc.perform(post("/api/v2/orders")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()));
    }

    @Test
    void 목록_조회_응답의_projectId는_UUID로_채워진다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        Funding funding = Funding.builder().id(1L).publicId(UUID.randomUUID()).memberId(memberId).projectId(PROJECT_ID)
                .projectTitle("프로젝트").status(FundingStatus.PENDING)
                .shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(3_000L).paymentExpiresAt(Instant.now().plusSeconds(1800))
                .lineItems(List.of()).createdAt(Instant.now()).build();
        when(orderQueryService.listMyOrders(eq(memberId), any(), any())).thenReturn(new PageImpl<>(List.of(funding)));

        // when & then
        mockMvc.perform(get("/api/v2/orders").header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].projectId").value(PROJECT_ID.toString()));
    }
}
