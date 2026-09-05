package com.fundit.project.presentation.controller;

import com.fundit.project.application.ai.FundingStoryService;
import com.fundit.project.domain.aifundingstory.FundingStoryResult;
import com.fundit.project.domain.aifundingstory.FundingStorySection;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.aifundingstory.AiFundingStorySessionJpaEntity;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FundingStoryController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, CurrentAdminArgumentResolver.class, WebConfig.class})
class FundingStoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FundingStoryService fundingStoryService;

    @Test
    void 세션_생성요청은_202를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AiFundingStorySessionJpaEntity session = AiFundingStorySessionJpaEntity.builder()
                .id(sessionId).projectId(1L).sellerId(sellerId).productDescription("설명")
                .status(AiFundingStorySessionJpaEntity.STATUS_COMPLETED).build();
        when(fundingStoryService.createSession(eq(sellerId), eq(projectId), eq("설명"), any(), any())).thenReturn(session);

        // when & then
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/ai/funding-story/sessions")
                        .header("X-Account-Id", sellerId.toString())
                        .contentType("application/json")
                        .content("{\"productDescription\":\"설명\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()));
    }

    @Test
    void 결과조회는_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        FundingStoryResult result = new FundingStoryResult(
                List.of(new FundingStorySection("INTRO", "제목", "본문", List.of())), List.of(), List.of());
        AiFundingStorySessionJpaEntity session = AiFundingStorySessionJpaEntity.builder()
                .id(sessionId).projectId(1L).sellerId(sellerId).productDescription("설명")
                .status(AiFundingStorySessionJpaEntity.STATUS_COMPLETED).result(result).additionalQuestions(List.of()).build();
        when(fundingStoryService.getSession(sellerId, sessionId)).thenReturn(session);

        // when & then
        mockMvc.perform(get("/api/v1/ai/funding-story/sessions/" + sessionId).header("X-Account-Id", sellerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.sections[0].type").value("INTRO"));
    }

    @Test
    void 결과반영은_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project project = Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(ProjectStatus.DRAFT)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(fundingStoryService.applyToProject(eq(sellerId), eq(sessionId), eq("OVERWRITE"), any())).thenReturn(project);

        // when & then
        mockMvc.perform(patch("/api/v1/ai/funding-story/sessions/" + sessionId + "/apply")
                        .header("X-Account-Id", sellerId.toString())
                        .contentType("application/json")
                        .content("{\"mode\":\"OVERWRITE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(publicId.toString()));
    }
}
