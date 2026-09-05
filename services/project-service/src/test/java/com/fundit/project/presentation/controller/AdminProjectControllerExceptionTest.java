package com.fundit.project.presentation.controller;

import com.fundit.project.application.project.ProjectReviewService;
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

/** 정상 흐름은 {@link AdminProjectControllerTest} 참고. */
@WebMvcTest(AdminProjectController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, CurrentAdminArgumentResolver.class, WebConfig.class})
class AdminProjectControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectReviewService projectReviewService;

    @Test
    void role이_admin이_아니면_403을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/projects/" + UUID.randomUUID() + "/review-decision")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .header("X-Account-Role", "member")
                        .contentType("application/json")
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void decision값이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/projects/" + UUID.randomUUID() + "/review-decision")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .header("X-Account-Role", "admin")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void decision값이_잘못되면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/projects/" + UUID.randomUUID() + "/review-decision")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .header("X-Account-Role", "admin")
                        .contentType("application/json")
                        .content("{\"decision\":\"WRONG\"}"))
                .andExpect(status().isBadRequest());
    }
}
