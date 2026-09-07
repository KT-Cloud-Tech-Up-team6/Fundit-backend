package com.fundit.project.presentation.controller;

import com.fundit.project.application.liveverification.LiveVerificationService;
import com.fundit.project.infrastructure.security.CurrentAdminArgumentResolver;
import com.fundit.project.infrastructure.security.CurrentMemberArgumentResolver;
import com.fundit.project.infrastructure.security.WebConfig;
import com.fundit.project.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link LiveVerificationControllerTest} 참고. */
@WebMvcTest(LiveVerificationController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, CurrentAdminArgumentResolver.class, WebConfig.class})
class LiveVerificationControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LiveVerificationService liveVerificationService;

    @Test
    void 필수값_없이_등록하면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + UUID.randomUUID() + "/live-verifications")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 인증헤더_없이_등록하면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + UUID.randomUUID() + "/live-verifications")
                        .contentType("application/json")
                        .content("{\"questionSummaryId\":\"live-q-1\",\"answer\":\"답변\"}"))
                .andExpect(status().isUnauthorized());
    }
}
