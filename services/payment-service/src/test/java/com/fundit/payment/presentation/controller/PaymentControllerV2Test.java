package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.payment.application.payment.PaymentConfirmService;
import com.fundit.payment.application.payment.PaymentCreateService;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * cross-service ID 통일(#69) — v2는 내부 해석 호출 없이 fundingId(UUID)를 그대로 받는다.
 */
@WebMvcTest(PaymentControllerV2.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class PaymentControllerV2Test {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";
    private static final UUID FUNDING_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentCreateService paymentCreateService;
    @MockitoBean
    private PaymentConfirmService paymentConfirmService;

    @Test
    void 결제_시도를_생성하면_UUID_fundingId를_그대로_전달한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(paymentCreateService.create(memberId, FUNDING_ID)).thenReturn(
                new PaymentCreateService.PaymentCreateResult(paymentId, "fundit-abc", 89_000L, "테스트 주문"));

        // when & then
        mockMvc.perform(post("/api/v2/payments")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundingId\": \"%s\"}".formatted(FUNDING_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.pgOrderId").value("fundit-abc"));
    }

    @Test
    void 결제_승인_응답의_fundingId는_UUID로_채워진다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant paidAt = Instant.parse("2026-09-08T05:23:11Z");
        when(paymentConfirmService.confirm(memberId, "pay_key", "fundit-abc", 89_000L)).thenReturn(
                new PaymentConfirmService.PaymentConfirmResult(paymentId, FUNDING_ID, "COMPLETED", PaymentMethod.CARD,
                        null, paidAt));

        // when & then
        mockMvc.perform(post("/api/v2/payments/confirm")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":"pay_key","orderId":"fundit-abc","amount":89000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.fundingId").value(FUNDING_ID.toString()));
    }
}
