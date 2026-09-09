package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.payment.application.refund.DefectRefundDecisionService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RefundController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class RefundControllerTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefundQueryService refundQueryService;
    @MockitoBean
    private DefectRefundRequestService defectRefundRequestService;
    @MockitoBean
    private DefectRefundDecisionService defectRefundDecisionService;
    @MockitoBean
    private ShippingDelayRefundService shippingDelayRefundService;

    @Test
    void 본인_환불내역을_조회하면_200을_반환한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-09-08T01:00:00Z");
        when(refundQueryService.listMyRefunds(eq(memberId), any())).thenReturn(
                new PageImpl<>(List.of(new RefundQueryService.RefundSummary(3L, 1024L, "DEFECT", "REQUESTED",
                        89_000L, requestedAt)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/refunds")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].refundId").value(3))
                .andExpect(jsonPath("$.content[0].triggerType").value("DEFECT"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void 하자환불을_신청하면_201을_반환한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(defectRefundRequestService.request(eq(memberId), eq(1024L), eq("[DAMAGED] 파손"), any()))
                .thenReturn(new DefectRefundRequestService.DefectRefundRequestResult(11L, "REQUESTED"));

        mockMvc.perform(post("/api/v1/refunds/defect")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundingId":1024,"defectType":"DAMAGED","reasonDetail":"파손","evidenceUrls":["https://cdn/a.jpg"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").value(11))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void 하자환불을_승인하면_200을_반환한다() throws Exception {
        UUID sellerId = UUID.randomUUID();
        when(defectRefundDecisionService.decide(sellerId, 11L, true, null))
                .thenReturn(new DefectRefundDecisionService.DefectDecisionResult(11L, "COMPLETED"));

        mockMvc.perform(patch("/api/v1/refunds/11/decision")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void 발송지연_취소를_신청하면_201을_반환한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(shippingDelayRefundService.requestCancel(memberId, 1024L))
                .thenReturn(new ShippingDelayRefundService.ShippingDelayRefundResult(12L, "COMPLETED"));

        mockMvc.perform(post("/api/v1/refunds/shipping-delay")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundingId\":1024}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").value(12));
    }
}
