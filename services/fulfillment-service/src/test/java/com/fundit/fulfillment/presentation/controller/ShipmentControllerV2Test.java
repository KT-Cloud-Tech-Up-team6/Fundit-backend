package com.fundit.fulfillment.presentation.controller;

import com.fundit.fulfillment.application.shipment.ShipmentService;
import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.fulfillment.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * cross-service ID 통일(#69) — v2는 내부 해석 호출 없이 projectId/fundingId(UUID)를 그대로 받는다.
 */
@WebMvcTest(ShipmentControllerV2.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class ShipmentControllerV2Test {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";
    private static final UUID PROJECT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID FUNDING_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShipmentService shipmentService;

    @Test
    void 발송정보를_등록하면_UUID_fundingId를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        Shipment shipment = Shipment.create(FUNDING_ID, PROJECT_ID);
        shipment.registerShipment("CJ대한통운", "123456789012");
        when(shipmentService.registerShipment(eq(PROJECT_ID), eq(FUNDING_ID), eq(sellerId), eq("CJ대한통운"), eq("123456789012")))
                .thenReturn(shipment);

        // when & then
        mockMvc.perform(post("/api/v2/projects/{projectId}/fundings/{fundingId}/shipment", PROJECT_ID, FUNDING_ID)
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\": \"CJ대한통운\", \"trackingNumber\": \"123456789012\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fundingId").value(FUNDING_ID.toString()))
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void 발송_전이면_PREPARING을_반환한다() throws Exception {
        // given
        UUID buyerId = UUID.randomUUID();
        when(shipmentService.getShipment(PROJECT_ID, FUNDING_ID, buyerId))
                .thenReturn(Shipment.create(FUNDING_ID, PROJECT_ID));

        // when & then
        mockMvc.perform(get("/api/v2/projects/{projectId}/fundings/{fundingId}/shipment", PROJECT_ID, FUNDING_ID)
                        .header("X-User-Id", buyerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fundingId").value(FUNDING_ID.toString()))
                .andExpect(jsonPath("$.status").value("PREPARING"));
    }

    @Test
    void 수령확인하면_200을_반환한다() throws Exception {
        // given
        UUID buyerId = UUID.randomUUID();
        Shipment shipment = Shipment.create(FUNDING_ID, PROJECT_ID);
        shipment.registerShipment("CJ대한통운", "123456789012");
        shipment.markDelivered(java.time.Instant.now());
        shipment.confirmReceipt(java.time.Instant.now(), false);
        when(shipmentService.confirmReceipt(PROJECT_ID, FUNDING_ID, buyerId)).thenReturn(shipment);

        // when & then
        mockMvc.perform(post("/api/v2/projects/{projectId}/fundings/{fundingId}/shipment/confirm-receipt",
                        PROJECT_ID, FUNDING_ID)
                        .header("X-User-Id", buyerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIPT_CONFIRMED"))
                .andExpect(jsonPath("$.fundingId").value(FUNDING_ID.toString()));
    }
}
