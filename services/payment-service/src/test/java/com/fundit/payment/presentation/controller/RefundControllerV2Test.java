package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.payment.application.refund.DefectRefundRequestService;
import com.fundit.payment.application.refund.RefundQueryService;
import com.fundit.payment.application.refund.ShippingDelayRefundService;
import com.fundit.payment.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
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
 * cross-service ID 통일(#69) — v2는 내부 해석 호출 없이 fundingId(UUID)를 그대로 받는다/돌려준다.
 */
@WebMvcTest(RefundControllerV2.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class RefundControllerV2Test {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";
    private static final UUID FUNDING_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefundQueryService refundQueryService;
    @MockitoBean
    private DefectRefundRequestService defectRefundRequestService;
    @MockitoBean
    private ShippingDelayRefundService shippingDelayRefundService;

    @Test
    void 목록_조회_응답의_fundingId는_UUID로_채워진다() throws Exception {
        UUID memberId = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-09-08T01:00:00Z");
        when(refundQueryService.listMyRefunds(eq(memberId), any())).thenReturn(
                new PageImpl<>(List.of(new RefundQueryService.RefundSummary(3L, FUNDING_ID, "DEFECT", "REQUESTED",
                        89_000L, requestedAt)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v2/refunds")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].refundId").value(3))
                .andExpect(jsonPath("$.content[0].fundingId").value(FUNDING_ID.toString()));
    }

    @Test
    void 하자환불을_신청하면_UUID_fundingId를_그대로_전달한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(defectRefundRequestService.request(eq(memberId), eq(FUNDING_ID), eq("[DAMAGED] 파손"), any()))
                .thenReturn(new DefectRefundRequestService.DefectRefundRequestResult(11L, "REQUESTED"));

        mockMvc.perform(post("/api/v2/refunds/defect")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundingId":"%s","defectType":"DAMAGED","reasonDetail":"파손","evidenceUrls":["https://cdn/a.jpg"]}
                                """.formatted(FUNDING_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").value(11));
    }

    @Test
    void 발송지연_취소를_신청하면_UUID_fundingId를_그대로_전달한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(shippingDelayRefundService.requestCancel(memberId, FUNDING_ID))
                .thenReturn(new ShippingDelayRefundService.ShippingDelayRefundResult(12L, "COMPLETED"));

        mockMvc.perform(post("/api/v2/refunds/shipping-delay")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundingId\":\"%s\"}".formatted(FUNDING_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").value(12));
    }
}
