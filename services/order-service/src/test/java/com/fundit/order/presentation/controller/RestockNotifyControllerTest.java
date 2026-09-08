package com.fundit.order.presentation.controller;

import com.fundit.order.application.restock.RestockNotifyService;
import com.fundit.order.infrastructure.security.CurrentMemberArgumentResolver;
import com.fundit.order.infrastructure.security.WebConfig;
import com.fundit.order.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestockNotifyController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, WebConfig.class})
class RestockNotifyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestockNotifyService restockNotifyService;

    @Test
    void 재입고_알림을_신청하면_200을_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();

        // when & then
        mockMvc.perform(post("/api/v1/reward-restock-notifications")
                        .header("X-Account-Id", memberId.toString())
                        .contentType("application/json")
                        .content("{\"rewardId\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardId").value(1))
                .andExpect(jsonPath("$.requested").value(true));
    }
}
