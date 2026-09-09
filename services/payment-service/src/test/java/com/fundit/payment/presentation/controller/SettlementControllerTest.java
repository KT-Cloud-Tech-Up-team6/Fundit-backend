package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.payment.application.settlement.OrderSettlementAggregateClient;
import com.fundit.payment.application.settlement.SettlementDisputeService;
import com.fundit.payment.application.settlement.SettlementDownloadService;
import com.fundit.payment.application.settlement.SettlementQueryService;
import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchType;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettlementController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class SettlementControllerTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementQueryService settlementQueryService;
    @MockitoBean
    private SettlementDownloadService settlementDownloadService;
    @MockitoBean
    private SettlementDisputeService settlementDisputeService;

    @Test
    void 정산_내역서를_조회하면_200을_반환한다() throws Exception {
        UUID sellerId = UUID.randomUUID();
        SettlementBatch batch = SettlementBatch.create(sellerId, SettlementBatchType.INTERIM,
                        Instant.now(), Instant.now(), 100_000L, 3_000L, 1_000L, 2_000L, List.of())
                .toBuilder().id(77L).build();
        when(settlementQueryService.getDetail(sellerId, 77L)).thenReturn(new SettlementQueryService.SettlementDetail(
                batch, List.of(new OrderSettlementAggregateClient.LineItemAggregate(5L, "리워드", "화이트", 2, 100_000L))));

        mockMvc.perform(get("/api/v1/settlements/77")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementBatchId").value(77))
                .andExpect(jsonPath("$.batchType").value("INTERIM"))
                .andExpect(jsonPath("$.grossAmount").value(100000))
                .andExpect(jsonPath("$.lineItems[0].rewardName").value("리워드"));
    }

    @Test
    void 다운로드_가능_검증만_통과하면_204를_반환한다() throws Exception {
        UUID sellerId = UUID.randomUUID();
        doNothing().when(settlementDownloadService).assertDownloadable(sellerId, 77L);

        mockMvc.perform(get("/api/v1/settlements/77/download")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    void 이의신청을_접수하면_201을_반환한다() throws Exception {
        UUID sellerId = UUID.randomUUID();
        when(settlementDisputeService.create(sellerId, 77L, "금액이 다릅니다.", List.of("https://cdn/e.pdf")))
                .thenReturn(new SettlementDisputeService.DisputeResult(9L, "RECEIVED"));

        mockMvc.perform(post("/api/v1/settlements/77/disputes")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"금액이 다릅니다.","evidenceUrls":["https://cdn/e.pdf"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.disputeId").value(9))
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }
}
