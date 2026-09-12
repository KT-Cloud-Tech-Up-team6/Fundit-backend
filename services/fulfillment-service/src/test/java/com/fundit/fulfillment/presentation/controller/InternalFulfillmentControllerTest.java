package com.fundit.fulfillment.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.fulfillment.application.shipment.FulfillmentStatusInternalService;
import com.fundit.fulfillment.application.shipment.FulfillmentStatusInternalService.FulfillmentStatusView;
import com.fundit.fulfillment.infrastructure.security.InternalEndpointConfig;
import com.fundit.fulfillment.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalFulfillmentController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class, InternalEndpointConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class InternalFulfillmentControllerTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FulfillmentStatusInternalService fulfillmentStatusInternalService;

    @Test
    void 내부_키가_있으면_배송상태를_반환한다() throws Exception {
        // given
        when(fulfillmentStatusInternalService.getStatus(1024L))
                .thenReturn(new FulfillmentStatusView(true, false, Instant.parse("2026-09-11T09:00:00Z"), null));

        // when & then
        mockMvc.perform(get("/internal/fundings/1024/fulfillment-status")
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAlreadyShipped").value(true))
                .andExpect(jsonPath("$.isDelayed").value(false));
    }

    @Test
    void 내부_키가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/internal/fundings/1024/fulfillment-status"))
                .andExpect(status().isUnauthorized());
    }
}
