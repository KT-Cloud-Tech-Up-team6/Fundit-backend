package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.application.payment.PaymentConfirmService;
import com.fundit.payment.application.payment.PaymentCreateService;
import com.fundit.payment.application.payment.TossWebhookService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class PaymentControllerTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";
    private static final UUID ORDER_ID = new UUID(2L, 1024L);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentCreateService paymentCreateService;
    @MockitoBean
    private PaymentConfirmService paymentConfirmService;
    @MockitoBean
    private TossWebhookService tossWebhookService;
    @MockitoBean
    private OrderFundingClient orderFundingClient;

    @Test
    void 결제_시도를_생성하면_201을_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(orderFundingClient.fetchByInternalId(1024L)).thenReturn(
                new OrderFundingClient.FundingSnapshot(memberId, UUID.randomUUID(), "PENDING", 89_000L, "테스트 주문", null,
                        ORDER_ID));
        when(paymentCreateService.create(memberId, ORDER_ID)).thenReturn(
                new PaymentCreateService.PaymentCreateResult(paymentId, "fundit-abc", 89_000L, "테스트 주문"));

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundingId\": 1024}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.pgOrderId").value("fundit-abc"))
                .andExpect(jsonPath("$.amount").value(89_000));
    }

    @Test
    void 결제_승인을_요청하면_200을_반환하고_v1_fundingId는_null이다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant paidAt = Instant.parse("2026-09-08T05:23:11Z");
        when(paymentConfirmService.confirm(memberId, "pay_key", "fundit-abc", 89_000L)).thenReturn(
                new PaymentConfirmService.PaymentConfirmResult(paymentId, ORDER_ID, "COMPLETED", PaymentMethod.CARD,
                        null, paidAt));

        // when & then
        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":"pay_key","orderId":"fundit-abc","amount":89000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.fundingId").doesNotExist());
    }

    @Test
    void 간편결제는_easyPayProvider를_응답에_포함한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(paymentConfirmService.confirm(memberId, "pay_key", "fundit-abc", 89_000L)).thenReturn(
                new PaymentConfirmService.PaymentConfirmResult(UUID.randomUUID(), ORDER_ID, "COMPLETED",
                        PaymentMethod.EASY_PAY, "KAKAOPAY", Instant.now()));

        mockMvc.perform(post("/api/v1/payments/confirm")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":"pay_key","orderId":"fundit-abc","amount":89000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.easyPayProvider").value("KAKAOPAY"));
    }

    @Test
    void 토스_웹훅은_로그인_없이_처리한다() throws Exception {
        mockMvc.perform(post("/api/v1/payments/webhook/toss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"PAYMENT_STATUS_CHANGED","data":{"paymentKey":"pay_1","status":"DONE","secret":"s"}}
                                """))
                .andExpect(status().isOk());

        verify(tossWebhookService).handle("PAYMENT_STATUS_CHANGED", "pay_1", "DONE", "s");
    }

    @Test
    void 웹훅_data가_없으면_null로_위임한다() throws Exception {
        mockMvc.perform(post("/api/v1/payments/webhook/toss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"PAYMENT_STATUS_CHANGED\"}"))
                .andExpect(status().isOk());

        verify(tossWebhookService).handle("PAYMENT_STATUS_CHANGED", null, null, null);
    }
}
