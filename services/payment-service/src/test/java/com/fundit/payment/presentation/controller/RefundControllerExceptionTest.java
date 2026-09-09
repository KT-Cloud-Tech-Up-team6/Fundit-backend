package com.fundit.payment.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.payment.application.refund.DefectRefundDecisionService;
import com.fundit.payment.application.refund.DefectRefundRequestService;
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
    private DefectRefundRequestService defectRefundRequestService;
    @MockitoBean
    private DefectRefundDecisionService defectRefundDecisionService;
    @MockitoBean
    private ShippingDelayRefundService shippingDelayRefundService;

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
        when(shippingDelayRefundService.requestCancel(memberId, 1024L))
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
}
