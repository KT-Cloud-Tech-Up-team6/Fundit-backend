package com.fundit.payment.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.payment.application.payment.PaymentConfirmService;
import com.fundit.payment.application.payment.PaymentCreateService;
import com.fundit.payment.application.payment.TossWebhookService;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class PaymentControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentCreateService paymentCreateService;
    @MockitoBean
    private PaymentConfirmService paymentConfirmService;
    @MockitoBean
    private TossWebhookService tossWebhookService;

    @Test
    void 내부키가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundingId\": 1024}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 주문이_PENDING이_아니면_409를_반환한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(paymentCreateService.create(memberId, 1024L))
                .thenThrow(new BusinessException(PaymentErrorCode.FUNDING_NOT_PENDING));

        mockMvc.perform(post("/api/v1/payments")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundingId\": 1024}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FUNDING_NOT_PENDING"));
    }

    @Test
    void 금액이_불일치하면_422를_반환한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(paymentConfirmService.confirm(any(), anyString(), anyString(), anyLong()))
                .thenThrow(new BusinessException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH));

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":"pay_key","orderId":"fundit-abc","amount":1}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
    }

    @Test
    void 웹훅_서명이_잘못되면_401을_반환한다() throws Exception {
        doThrow(new BusinessException(PaymentErrorCode.WEBHOOK_SIGNATURE_INVALID))
                .when(tossWebhookService).handle(any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/payments/webhook/toss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"PAYMENT_STATUS_CHANGED","data":{"paymentKey":"pay","status":"DONE","secret":"bad"}}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
    }

    @Test
    void 존재하지_않는_결제를_승인하면_404를_반환한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(paymentConfirmService.confirm(any(), anyString(), anyString(), anyLong()))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":"pay_key","orderId":"missing","amount":89000}
                                """))
                .andExpect(status().isNotFound());
    }
}
