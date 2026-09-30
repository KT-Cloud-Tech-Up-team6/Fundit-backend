package com.fundit.order.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.live.LiveStatusClient;
import com.fundit.order.application.order.LiveOrderStatsService;
import com.fundit.order.application.order.OrderCancelService;
import com.fundit.order.application.order.OrderCreateService;
import com.fundit.order.application.order.OrderPreviewService;
import com.fundit.order.application.order.OrderQueryService;
import com.fundit.order.domain.OrderErrorCode;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.order.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link OrderControllerTest} 참고. */
@WebMvcTest(OrderController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class OrderControllerExceptionTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";

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
    @MockitoBean
    private LiveOrderStatsService liveOrderStatsService;
    @MockitoBean
    private LiveStatusClient liveStatusClient;

    @Test
    void 재고가_부족하면_409를_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        when(orderCreateService.create(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any()))
                .thenThrow(new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK));

        // when & then
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType("application/json")
                        .content("""
                                {"projectId": "%s", "lineItems": [{"rewardId":1,"quantity":100}],
                                 "shippingAddress": {"recipientName":"홍길동","phoneNumber":"010-1234-5678","zipcode":"12345","addressLine1":"주소"}}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void 배송지_연락처가_휴대폰_번호_형식이_아니면_400이다() throws Exception {
        // given (QA-061·062·063, #209)
        UUID memberId = UUID.randomUUID();

        // when & then
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType("application/json")
                        .content("""
                                {"projectId": "%s", "lineItems": [{"rewardId":1,"quantity":1}],
                                 "shippingAddress": {"recipientName":"홍길동","phoneNumber":"abc","zipcode":"12345","addressLine1":"주소"}}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 존재하지_않는_주문을_취소하면_404를_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderCancelService.cancel(memberId, orderId, null, null)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        // when & then
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void 취소할_수_없는_상태면_422를_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderCancelService.cancel(memberId, orderId, null, null))
                .thenThrow(new BusinessException(OrderErrorCode.ORDER_NOT_CANCELLABLE));

        // when & then
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 목록_필터의_시작일이_종료일보다_늦으면_400이다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();

        // when & then
        mockMvc.perform(get("/api/v1/orders").header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .param("from", "2026-09-30").param("to", "2026-09-01"))
                .andExpect(status().isBadRequest());
    }
}
