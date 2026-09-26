package com.fundit.payment.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.application.refund.DefectRefundDecisionService;
import com.fundit.payment.application.refund.PostShipmentRefundRequestService;
import com.fundit.payment.application.refund.PostShipmentRefundRequestService.PostShipmentRefundRequestResult;
import com.fundit.payment.application.refund.RefundEstimateService;
import com.fundit.payment.application.refund.RefundEvidenceUploadService;
import com.fundit.payment.application.refund.RefundQueryService;
import com.fundit.payment.application.refund.ShippingDelayRefundService;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RefundController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class RefundControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefundQueryService refundQueryService;
    @MockitoBean
    private PostShipmentRefundRequestService postShipmentRefundRequestService;
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
    void 증빙이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/refunds/defect")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundingId\":1024,\"defectType\":\"DAMAGED\",\"evidenceUrls\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 이미_발송됐으면_409를_반환한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        UUID orderId = new UUID(2L, 1024L);
        when(orderFundingClient.fetchByInternalId(1024L)).thenReturn(
                new OrderFundingClient.FundingSnapshot(memberId, UUID.randomUUID(), "GOAL_ACHIEVED", 89_000L, "주문", null,
                        orderId, 0L, 0L));
        when(shippingDelayRefundService.requestCancel(memberId, orderId))
                .thenThrow(new BusinessException(PaymentErrorCode.ALREADY_SHIPPED));

        mockMvc.perform(post("/api/v1/refunds/shipping-delay")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundingId\":1024}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SHIPPED"));
    }

    @Test
    void 반려_사유가_없으면_400을_반환한다() throws Exception {
        UUID sellerId = UUID.randomUUID();
        when(defectRefundDecisionService.decide(any(), anyLong(), anyBoolean(), any()))
                .thenThrow(new BusinessException(PaymentErrorCode.REASON_REQUIRED));

        mockMvc.perform(patch("/api/v1/refunds/11/decision")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }

    @Test
    void 완료된_결제가_없으면_예상액_조회는_404를_반환한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        UUID orderId = new UUID(2L, 1024L);
        when(refundEstimateService.estimate(memberId, orderId, null, false))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/refunds/estimate")
                        .param("orderId", orderId.toString())
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
