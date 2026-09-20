package com.fundit.project.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.project.application.ai.FundingStoryAiContracts.ChatAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.ConfirmResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.LatestSessionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicRunResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicStoryResult;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FundingStoryController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class FundingStoryControllerAdditionalTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FundingStoryService fundingStoryService;

    @Test
    void 세션_조회_시작_메시지_확인_결과조회_경로를_공개계약으로_노출한다() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        SessionResponse session = new SessionResponse(sessionId, 2, 1, List.of(), List.of(), null, chatId);

        when(fundingStoryService.getLatestSession(sellerId, projectId)).thenReturn(new LatestSessionResponse(session));
        when(fundingStoryService.getSession(sellerId, projectId, sessionId)).thenReturn(session);
        when(fundingStoryService.startSession(sellerId, projectId, sessionId))
                .thenReturn(new ChatAcceptedResponse(chatId, "accepted"));
        when(fundingStoryService.addMessage(sellerId, projectId, sessionId, any()))
                .thenReturn(new ChatAcceptedResponse(chatId, "accepted"));
        when(fundingStoryService.confirmSession(sellerId, projectId, sessionId,
                new com.fundit.project.application.ai.FundingStoryAiContracts.ConfirmRequest(2)))
                .thenReturn(new ConfirmResponse(sessionId, 2));
        when(fundingStoryService.getRun(sellerId, projectId, runId))
                .thenReturn(new PublicRunResponse(runId, "succeeded",
                        new PublicStoryResult(null, List.of()), List.of(), null));

        mockMvc.perform(get("/api/v1/ai/sessions/latest")
                        .header("X-User-Id", sellerId)
                        .header("X-Project-Id", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_id").value(sessionId.toString()));
        mockMvc.perform(get("/api/v1/ai/sessions/{sessionId}", sessionId)
                        .header("X-User-Id", sellerId)
                        .header("X-Project-Id", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2));
        mockMvc.perform(post("/api/v1/ai/sessions/{sessionId}/start", sessionId)
                        .header("X-User-Id", sellerId)
                        .header("X-Project-Id", projectId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.chat_id").value(chatId.toString()));
        mockMvc.perform(post("/api/v1/ai/sessions/{sessionId}/messages", sessionId)
                        .header("X-User-Id", sellerId)
                        .header("X-Project-Id", projectId)
                        .contentType("application/json")
                        .content("{\"message_id\":\"m-1\",\"revision\":2,\"text\":\"답변\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/ai/sessions/{sessionId}/confirm", sessionId)
                        .header("X-User-Id", sellerId)
                        .header("X-Project-Id", projectId)
                        .contentType("application/json")
                        .content("{\"revision\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed_revision").value(2));
        mockMvc.perform(get("/api/v1/ai/runs/{runId}", runId)
                        .header("X-User-Id", sellerId)
                        .header("X-Project-Id", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("succeeded"));
    }

    @Test
    void 채팅_SSE_중계는_이벤트_스트림_콘텐츠_타입을_유지한다() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        when(fundingStoryService.openChatEvents(sellerId, projectId, chatId)).thenReturn(
                new com.fundit.project.application.ai.FundingStoryAiClient.ChatEventStream(
                        new java.io.ByteArrayInputStream("data: ready\n\n".getBytes()),
                        () -> { }));

        mockMvc.perform(get("/api/v1/ai/chats/{chatId}/events", chatId)
                        .header("X-User-Id", sellerId)
                        .header("X-Project-Id", projectId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"));
    }
}
