package com.fundit.order.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.order.OrderCancelService;
import com.fundit.order.application.order.OrderCreateService;
import com.fundit.order.application.order.OrderPreviewService;
import com.fundit.order.application.order.OrderQueryService;
import com.fundit.order.domain.OrderErrorCode;
import com.fundit.order.infrastructure.security.CurrentMemberArgumentResolver;
import com.fundit.order.infrastructure.security.WebConfig;
import com.fundit.order.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link OrderControllerTest} 참고. */
@WebMvcTest(OrderController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, WebConfig.class})
class OrderControllerExceptionTest {

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

    @Test
    void 재고가_부족하면_409를_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        when(orderCreateService.create(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK));

        // when & then
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-Account-Id", memberId.toString())
                        .contentType("application/json")
                        .content("""
                                {"projectId": 123, "lineItems": [{"rewardId":1,"quantity":100}],
                                 "shippingAddress": {"recipientName":"홍길동","phoneNumber":"010","zipcode":"12345","addressLine1":"주소"}}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void 존재하지_않는_주문을_취소하면_404를_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderCancelService.cancel(memberId, orderId)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        // when & then
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").header("X-Account-Id", memberId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 취소할_수_없는_상태면_422를_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderCancelService.cancel(memberId, orderId))
                .thenThrow(new BusinessException(OrderErrorCode.ORDER_NOT_CANCELLABLE));

        // when & then
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").header("X-Account-Id", memberId.toString()))
                .andExpect(status().isUnprocessableEntity());
    }
}
