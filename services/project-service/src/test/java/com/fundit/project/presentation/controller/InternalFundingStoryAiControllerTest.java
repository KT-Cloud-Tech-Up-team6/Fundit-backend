package com.fundit.project.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCompletionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.UploadTarget;
import com.fundit.project.application.ai.FundingStoryAiContracts.UploadTargetsRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.UploadTargetsResponse;
import com.fundit.project.application.ai.FundingStoryService;
import com.fundit.project.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalFundingStoryAiController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
class InternalFundingStoryAiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FundingStoryService fundingStoryService;

    @Test
    void AI_업로드_대상_발급과_완료_callback을_내부계약으로_노출한다() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(fundingStoryService.createUploadTargets(eqProject(projectId), any(UploadTargetsRequest.class)))
                .thenReturn(new UploadTargetsResponse(List.of(new UploadTarget(
                        "hero", "https://upload", "https://file", Instant.EPOCH))));
        when(fundingStoryService.completeRun(eqProject(projectId), eqRun(runId), any()))
                .thenReturn(new RunCompletionResponse(runId, "succeeded"));

        mockMvc.perform(post("/internal/ai/media/upload-targets")
                        .header("X-Project-Id", projectId)
                        .contentType("application/json")
                        .content("{\"outputs\":[{\"slot_id\":\"hero\",\"file_name\":\"hero.png\",\"content_type\":\"image/png\",\"file_size\":100}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets[0].slot_id").value("hero"));
        mockMvc.perform(post("/internal/ai/runs/{runId}/completion", runId)
                        .header("X-Project-Id", projectId)
                        .contentType("application/json")
                        .content("{\"status\":\"failed\",\"successful_images\":[],\"failed_slots\":[],\"error\":{\"code\":\"E\",\"message\":\"실패\",\"retryable\":true}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run_id").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("succeeded"));
    }

    private UUID eqProject(UUID value) {
        return value;
    }

    private UUID eqRun(UUID value) {
        return value;
    }
}
