package com.fundit.order.presentation.controller;

import com.fundit.order.application.order.OrderCancelService;
import com.fundit.order.application.order.OrderCreateService;
import com.fundit.order.application.order.OrderPreviewService;
import com.fundit.order.application.order.OrderPricingService;
import com.fundit.order.application.order.OrderQueryService;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.funding.ShippingAddress;
import com.fundit.order.infrastructure.security.CurrentMemberArgumentResolver;
import com.fundit.order.infrastructure.security.WebConfig;
import com.fundit.order.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
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

@WebMvcTest(OrderController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, WebConfig.class})
class OrderControllerTest {

    private static final String REQUEST_BODY = """
            {
              "projectId": 123,
              "lineItems": [ { "rewardId": 1, "quantity": 1, "optionValueIds": [] } ],
              "shippingAddress": { "recipientName": "홍길동", "phoneNumber": "010-1234-5678",
                "zipcode": "12345", "addressLine1": "서울시", "addressLine2": "101동" }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderPreviewService orderPreviewService;
    @MockitoBean
    private OrderCreateService orderCreateService;
    @MockitoBean
    private OrderQueryService orderQueryService;
    @MockitoBean
    private OrderCancelService orderCancelService;

    private Funding funding(UUID memberId, UUID publicId, FundingStatus status) {
        return Funding.builder().id(1L).publicId(publicId).memberId(memberId).projectId(123L).projectTitle("프로젝트")
                .status(status).shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(3_000L).paymentExpiresAt(Instant.now().plusSeconds(1800))
                .lineItems(List.of()).createdAt(Instant.now()).build();
    }

    @Test
    void 미리보기_요청하면_금액이_계산되어_200을_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        var pricing = new OrderPricingService.PricingResult(10_000L, 3_000L, 0L, 13_000L, List.of(), List.of(), List.of());
        when(orderPreviewService.preview(eq(memberId), eq(123L), any(), any())).thenReturn(pricing);

        // when & then
        mockMvc.perform(post("/api/v1/orders/preview")
                        .header("X-Account-Id", memberId.toString())
                        .contentType("application/json")
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalAmount").value(13_000));
    }

    @Test
    void 인증헤더가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/orders/preview")
                        .contentType("application/json")
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 필수값이_비어있으면_400을_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();

        // when & then — lineItems가 빈 배열
        mockMvc.perform(post("/api/v1/orders/preview")
                        .header("X-Account-Id", memberId.toString())
                        .contentType("application/json")
                        .content("""
                                {"projectId": 123, "lineItems": [],
                                 "shippingAddress": {"recipientName":"홍길동","phoneNumber":"010","zipcode":"12345","addressLine1":"주소"}}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 주문을_생성하면_201을_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Funding funding = funding(memberId, orderId, FundingStatus.PENDING);
        when(orderCreateService.create(eq(memberId), eq(123L), any(), any(), any()))
                .thenReturn(new OrderCreateService.OrderCreateResult(funding, 13_000L));

        // when & then
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-Account-Id", memberId.toString())
                        .contentType("application/json")
                        .content(REQUEST_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void 내_참여_목록을_조회한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        when(orderQueryService.listMyOrders(eq(memberId), any(), any()))
                .thenReturn(new PageImpl<>(List.of(funding(memberId, UUID.randomUUID(), FundingStatus.PENDING))));

        // when & then
        mockMvc.perform(get("/api/v1/orders").header("X-Account-Id", memberId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void 참여_상세를_조회한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Funding funding = funding(memberId, orderId, FundingStatus.PENDING);
        when(orderQueryService.getDetail(memberId, orderId))
                .thenReturn(new OrderQueryService.FundingDetail(funding, 0L));

        // when & then
        mockMvc.perform(get("/api/v1/orders/" + orderId).header("X-Account-Id", memberId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    @Test
    void 참여를_취소한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderCancelService.cancel(memberId, orderId))
                .thenReturn(funding(memberId, orderId, FundingStatus.CANCELLED_BY_MEMBER));

        // when & then
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").header("X-Account-Id", memberId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED_BY_MEMBER"));
    }
}
