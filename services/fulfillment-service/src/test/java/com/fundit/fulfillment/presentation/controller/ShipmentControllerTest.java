package com.fundit.fulfillment.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.fulfillment.application.funding.OrderFundingClient;
import com.fundit.fulfillment.application.funding.OrderFundingClient.FundingSnapshot;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.application.shipment.ShipmentService;
import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShipmentController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class ShipmentControllerTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";
    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final UUID FUNDING_ID = UUID.fromString("00000000-0000-0000-0000-000000001024");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShipmentService shipmentService;
    @MockitoBean
    private ProjectOwnershipClient projectOwnershipClient;
    @MockitoBean
    private OrderFundingClient orderFundingClient;

    @BeforeEach
    void setUp() {
        when(projectOwnershipClient.getPublicId(123L)).thenReturn(PROJECT_ID);
        when(orderFundingClient.fetchByInternalId(1024L))
                .thenReturn(new FundingSnapshot(PROJECT_ID, UUID.randomUUID(), FUNDING_ID));
    }

    @Test
    void 발송정보를_등록하면_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        Shipment shipment = Shipment.create(FUNDING_ID, PROJECT_ID);
        shipment.registerShipment("CJ대한통운", "123456789012");
        when(shipmentService.registerShipment(eq(PROJECT_ID), eq(FUNDING_ID), eq(sellerId), eq("CJ대한통운"), eq("123456789012")))
                .thenReturn(shipment);

        // when & then
        mockMvc.perform(post("/api/v1/projects/123/fundings/1024/shipment")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\": \"CJ대한통운\", \"trackingNumber\": \"123456789012\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.carrier").value("CJ대한통운"))
                .andExpect(jsonPath("$.fundingId").doesNotExist());
    }

    @Test
    void 이미_발송된_건이면_409를_반환한다() throws Exception {
        // given
        when(shipmentService.registerShipment(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(com.fundit.fulfillment.domain.FulfillmentErrorCode.ALREADY_SHIPPED));

        // when & then
        mockMvc.perform(post("/api/v1/projects/123/fundings/1024/shipment")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\": \"CJ대한통운\", \"trackingNumber\": \"123456789012\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void 발송_전이면_PREPARING을_반환한다() throws Exception {
        // given
        UUID buyerId = UUID.randomUUID();
        when(shipmentService.getShipment(PROJECT_ID, FUNDING_ID, buyerId)).thenReturn(Shipment.create(FUNDING_ID, PROJECT_ID));

        // when & then
        mockMvc.perform(get("/api/v1/projects/123/fundings/1024/shipment")
                        .header("X-User-Id", buyerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.canConfirmReceipt").value(false));
    }

    @Test
    void 타인_funding을_조회하면_403을_반환한다() throws Exception {
        // given
        when(shipmentService.getShipment(any(), any(), any()))
                .thenThrow(new BusinessException(CommonErrorCode.FORBIDDEN));

        // when & then
        mockMvc.perform(get("/api/v1/projects/123/fundings/1024/shipment")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isForbidden());
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
        mockMvc.perform(post("/api/v1/projects/123/fundings/1024/shipment/confirm-receipt")
                        .header("X-User-Id", buyerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIPT_CONFIRMED"));
    }

    @Test
    void 배송완료_전_수령확인시도하면_422를_반환한다() throws Exception {
        // given
        when(shipmentService.confirmReceipt(any(), any(), any()))
                .thenThrow(new BusinessException(com.fundit.fulfillment.domain.FulfillmentErrorCode.NOT_YET_DELIVERED));

        // when & then
        mockMvc.perform(post("/api/v1/projects/123/fundings/1024/shipment/confirm-receipt")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isUnprocessableEntity());
    }
}
