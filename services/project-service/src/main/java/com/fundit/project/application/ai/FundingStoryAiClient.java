package com.fundit.project.application.ai;

import com.fundit.project.application.ai.FundingStoryAiContracts.ChatAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.ConfirmRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.ConfirmResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.FundingStoryContext;
import com.fundit.project.application.ai.FundingStoryAiContracts.LatestSessionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.MessageRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.SessionResponse;

import java.io.InputStream;
import java.util.UUID;

/** BE → Funding Story AI outbound port. */
public interface FundingStoryAiClient {

    SessionResponse createSession(UUID projectId, FundingStoryContext context);

    LatestSessionResponse getLatestSession(UUID projectId);

    SessionResponse getSession(UUID projectId, UUID sessionId);

    ChatAcceptedResponse startSession(UUID projectId, UUID sessionId);

    ChatAcceptedResponse addMessage(UUID projectId, UUID sessionId, MessageRequest request);

    ChatEventStream openChatEvents(UUID projectId, UUID chatId);

    ConfirmResponse confirmSession(UUID projectId, UUID sessionId, ConfirmRequest request);

    RunAcceptedResponse createRun(UUID projectId, RunCreateRequest request);

    record ChatEventStream(InputStream body, Runnable onClose) implements AutoCloseable {
        @Override
        public void close() {
            onClose.run();
        }
    }
}
