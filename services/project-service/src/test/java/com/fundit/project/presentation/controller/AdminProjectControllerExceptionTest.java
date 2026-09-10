package com.fundit.project.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.project.application.project.ProjectReviewService;
import com.fundit.project.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link AdminProjectControllerTest} 참고. */
@WebMvcTest(AdminProjectController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class AdminProjectControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectReviewService projectReviewService;

    @Test
    void role이_admin이_아니면_403을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/projects/" + UUID.randomUUID() + "/review-decision")
                        .header("X-User-Id", UUID.randomUUID().toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .header("X-User-Roles", "MEMBER")
                        .contentType("application/json")
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void decision값이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/projects/" + UUID.randomUUID() + "/review-decision")
                        .header("X-User-Id", UUID.randomUUID().toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .header("X-User-Roles", "ADMIN")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void decision값이_잘못되면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/projects/" + UUID.randomUUID() + "/review-decision")
                        .header("X-User-Id", UUID.randomUUID().toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .header("X-User-Roles", "ADMIN")
                        .contentType("application/json")
                        .content("{\"decision\":\"WRONG\"}"))
                .andExpect(status().isBadRequest());
    }
}
