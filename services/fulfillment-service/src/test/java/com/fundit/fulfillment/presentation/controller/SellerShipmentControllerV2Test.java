package com.fundit.fulfillment.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.fulfillment.application.shipment.ShipmentService;
import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 판매자 발송 목록 화면이 한 페이지분 발송정보를 한 번에 받아가는 배치 조회. */
@WebMvcTest(SellerShipmentControllerV2.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class SellerShipmentControllerV2Test {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";
    private static final UUID PROJECT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID FUNDING_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShipmentService shipmentService;

    @Test
    void 발송정보를_배치로_조회하면_송장을_그대로_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        Shipment shipped = Shipment.create(FUNDING_ID, PROJECT_ID);
        shipped.registerShipment("CJ대한통운", "123456789012");
        when(shipmentService.listForSeller(eq(PROJECT_ID), eq(sellerId), eq(List.of(FUNDING_ID))))
                .thenReturn(List.of(shipped));

        // when & then
        mockMvc.perform(get("/api/v2/projects/{projectId}/shipments", PROJECT_ID)
                        .param("fundingIds", FUNDING_ID.toString())
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fundingId").value(FUNDING_ID.toString()))
                .andExpect(jsonPath("$[0].status").value("SHIPPED"))
                .andExpect(jsonPath("$[0].trackingNumber").value("123456789012"));
    }

    @Test
    void fundingIds가_상한을_넘으면_400을_반환한다() throws Exception {
        // given — 목록 한 페이지용 API라 무제한 IN 조회를 허용하지 않는다.
        String tooMany = Stream.generate(UUID::randomUUID).limit(101)
                .map(UUID::toString).collect(Collectors.joining(","));

        // when & then
        mockMvc.perform(get("/api/v2/projects/{projectId}/shipments", PROJECT_ID)
                        .param("fundingIds", tooMany)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 남의_프로젝트_발송정보는_조회할_수_없다() throws Exception {
        // given
        when(shipmentService.listForSeller(eq(PROJECT_ID), any(), any()))
                .thenThrow(new com.fundit.common.error.BusinessException(com.fundit.common.error.CommonErrorCode.FORBIDDEN));

        // when & then
        mockMvc.perform(get("/api/v2/projects/{projectId}/shipments", PROJECT_ID)
                        .param("fundingIds", FUNDING_ID.toString())
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isForbidden());
    }
}
