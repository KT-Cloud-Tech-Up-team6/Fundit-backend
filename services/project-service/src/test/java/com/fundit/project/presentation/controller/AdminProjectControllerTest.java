package com.fundit.project.presentation.controller;

import com.fundit.project.application.project.ProjectReviewService;
import com.fundit.project.application.project.ReviewDecision;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectStatus;
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

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminProjectController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, CurrentAdminArgumentResolver.class, WebConfig.class})
class AdminProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectReviewService projectReviewService;

    @Test
    void 관리자가_승인하면_200과_ONGOING_상태를_반환한다() throws Exception {
        // given
        UUID adminId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project ongoing = Project.builder()
                .id(1L).publicId(publicId).sellerId(UUID.randomUUID()).status(ProjectStatus.ONGOING)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectReviewService.decide(eq(adminId), eq(publicId), eq(ReviewDecision.APPROVED), isNull()))
                .thenReturn(ongoing);

        // when & then
        mockMvc.perform(post("/api/v1/admin/projects/" + publicId + "/review-decision")
                        .header("X-Account-Id", adminId.toString())
                        .header("X-Account-Role", "admin")
                        .contentType("application/json")
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONGOING"));
    }
}
