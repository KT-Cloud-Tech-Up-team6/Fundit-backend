package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.application.media.MediaStorageClient;
import com.fundit.payment.application.refund.DefectRefundDecisionService;
import com.fundit.payment.application.refund.DefectRefundRequestService;
import com.fundit.payment.application.refund.RefundEstimateService;
import com.fundit.payment.application.refund.RefundEvidenceUploadService;
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
    private static final UUID ORDER_ID = new UUID(2L, 1024L);

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
    @MockitoBean
    private OrderFundingClient orderFundingClient;
    @MockitoBean
    private RefundEvidenceUploadService refundEvidenceUploadService;
    @MockitoBean
    private RefundEstimateService refundEstimateService;

    @Test
    void 본인_환불내역을_조회하면_200을_반환한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-09-08T01:00:00Z");
        when(refundQueryService.listMyRefunds(eq(memberId), any(), any(), any())).thenReturn(
                new PageImpl<>(List.of(new RefundQueryService.RefundSummary(3L, ORDER_ID, "DEFECT", "REQUESTED",
                        89_000L, requestedAt, null, null, null, null)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/refunds")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].refundId").value(3))
                .andExpect(jsonPath("$.content[0].triggerType").value("DEFECT"))
                .andExpect(jsonPath("$.content[0].fundingId").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void 하자환불을_신청하면_201을_반환한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(orderFundingClient.fetchByInternalId(1024L)).thenReturn(
                new OrderFundingClient.FundingSnapshot(memberId, UUID.randomUUID(), "GOAL_ACHIEVED", 89_000L, "주문", null,
                        ORDER_ID, 0L, 0L));
        when(defectRefundRequestService.request(eq(memberId), eq(ORDER_ID), eq("[DAMAGED] 파손"), any()))
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
        when(orderFundingClient.fetchByInternalId(1024L)).thenReturn(
                new OrderFundingClient.FundingSnapshot(memberId, UUID.randomUUID(), "GOAL_ACHIEVED", 89_000L, "주문", null,
                        ORDER_ID, 0L, 0L));
        when(shippingDelayRefundService.requestCancel(memberId, ORDER_ID))
                .thenReturn(new ShippingDelayRefundService.ShippingDelayRefundResult(12L, "COMPLETED"));

        mockMvc.perform(post("/api/v1/refunds/shipping-delay")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundingId\":1024}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").value(12));
    }

    @Test
    void 증빙_업로드_주소를_발급받는다() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(refundEvidenceUploadService.issueUploadUrl(eq(memberId), eq(ORDER_ID), eq("photo.jpg"),
                eq("image/jpeg"), eq(1024L)))
                .thenReturn(new MediaStorageClient.PresignedUpload("https://s3/put-url", "https://cdn/refunds/x.jpg"));

        mockMvc.perform(post("/api/v1/refunds/evidence/upload-url")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId": "%s", "fileName": "photo.jpg", "contentType": "image/jpeg", "fileSize": 1024}
                                """.formatted(ORDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://s3/put-url"))
                .andExpect(jsonPath("$.fileUrl").value("https://cdn/refunds/x.jpg"));
    }

    @Test
    void 환불_예상액을_조회하면_200을_반환한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(refundEstimateService.estimate(memberId, ORDER_ID)).thenReturn(
                new RefundEstimateService.RefundEstimate(ORDER_ID, 89_000L, 3_000L, 2_000L, 90_000L));

        mockMvc.perform(get("/api/v1/refunds/estimate")
                        .param("orderId", ORDER_ID.toString())
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.rewardAmount").value(89_000))
                .andExpect(jsonPath("$.shippingFee").value(3_000))
                .andExpect(jsonPath("$.discountAmount").value(2_000))
                .andExpect(jsonPath("$.refundAmount").value(90_000));
    }
}
