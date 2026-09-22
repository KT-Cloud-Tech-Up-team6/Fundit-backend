package com.fundit.project.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicRunCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.SessionResponse;
import com.fundit.project.application.ai.FundingStoryService;
import com.fundit.project.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FundingStoryController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class FundingStoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FundingStoryService fundingStoryService;

    @Test
    void 빈_요청으로_세션을_생성하고_AI의_공개_ID를_그대로_반환한다() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        SessionResponse response = new SessionResponse(
                sessionId, 1, null, List.of(), List.of("story"), null, null);
        when(fundingStoryService.createSession(sellerId, projectId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/ai/sessions")
                        .header("X-User-Id", sellerId)
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .header("X-Project-Id", projectId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.session_id").value(sessionId.toString()))
                .andExpect(jsonPath("$.revision").value(1));
    }

    @Test
    void 전체생성은_같은_api_v1_ai_경로에서_202를_반환한다() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        PublicRunCreateRequest request = new PublicRunCreateRequest(sessionId, 3, "client-run-key");
        when(fundingStoryService.createRun(sellerId, projectId, request))
                .thenReturn(new RunAcceptedResponse(runId, "queued"));

        mockMvc.perform(post("/api/v1/ai/runs")
                        .header("X-User-Id", sellerId)
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .header("X-Project-Id", projectId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "session_id": "%s",
                                  "confirmed_revision": 3,
                                  "idempotency_key": "client-run-key"
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.run_id").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("queued"));
    }
}
