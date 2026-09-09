package com.fundit.payment.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.payment.application.settlement.SettlementDisputeService;
import com.fundit.payment.application.settlement.SettlementDownloadService;
import com.fundit.payment.application.settlement.SettlementQueryService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettlementController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class SettlementControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementQueryService settlementQueryService;
    @MockitoBean
    private SettlementDownloadService settlementDownloadService;
    @MockitoBean
    private SettlementDisputeService settlementDisputeService;

    @Test
    void 다운로드가_미구현이면_503을_반환한다() throws Exception {
        UUID sellerId = UUID.randomUUID();
        doThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "정산 내역서 다운로드 기능은 준비 중입니다."))
                .when(settlementDownloadService).assertDownloadable(sellerId, 77L);

        mockMvc.perform(get("/api/v1/settlements/77/download")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void 이의신청_기간이_지나면_409를_반환한다() throws Exception {
        UUID sellerId = UUID.randomUUID();
        when(settlementDisputeService.create(any(), anyLong(), any(), any()))
                .thenThrow(new BusinessException(PaymentErrorCode.DISPUTE_PERIOD_EXPIRED));

        mockMvc.perform(post("/api/v1/settlements/77/disputes")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"금액이 다릅니다.\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_PERIOD_EXPIRED"));
    }
}
