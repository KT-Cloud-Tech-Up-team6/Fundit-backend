package com.fundit.project.application.ai;

import com.fundit.project.application.ai.FundingStoryAiContracts.AsyncError;
import com.fundit.project.application.ai.FundingStoryAiContracts.CategoryFact;
import com.fundit.project.application.ai.FundingStoryAiContracts.ChatAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.ChatMessage;
import com.fundit.project.application.ai.FundingStoryAiContracts.ConfirmRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.ConfirmResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.FailedSlot;
import com.fundit.project.application.ai.FundingStoryAiContracts.FundingStoryContext;
import com.fundit.project.application.ai.FundingStoryAiContracts.GeneratedBody;
import com.fundit.project.application.ai.FundingStoryAiContracts.GeneratedContentBlock;
import com.fundit.project.application.ai.FundingStoryAiContracts.LatestSessionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.MessageRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.OutputDescriptor;
import com.fundit.project.application.ai.FundingStoryAiContracts.ProjectFact;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicContentBlock;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicRunCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicRunResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicSessionCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicStoryResult;
import com.fundit.project.application.ai.FundingStoryAiContracts.RewardFact;
import com.fundit.project.application.ai.FundingStoryAiContracts.RewardOption;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCompletionRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCompletionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.SessionCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.SessionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.SourceImageRef;
import com.fundit.project.application.ai.FundingStoryAiContracts.StorySummary;
import com.fundit.project.application.ai.FundingStoryAiContracts.StrengthSummary;
import com.fundit.project.application.ai.FundingStoryAiContracts.SuccessfulImage;
import com.fundit.project.application.ai.FundingStoryAiContracts.UploadTarget;
import com.fundit.project.application.ai.FundingStoryAiContracts.UploadTargetsRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.UploadTargetsResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FundingStoryAiContractsUnitTest {

    @Test
    void wire_DTO는_현재_통합계약의_모든_레코드_형태를_생성할_수_있다() {
        UUID sessionId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        CategoryFact category = new CategoryFact("테크", "가전");
        ProjectFact project = new ProjectFact("SOLE", category, "프로젝트", 1_000_000L);
        RewardOption option = new RewardOption("색상", List.of("화이트"));
        RewardFact reward = new RewardFact(1L, "리워드", "설명", 10_000L, false, null, false,
                List.of(option));
        SourceImageRef sourceImage = new SourceImageRef(
                "project.cover", null, "https://signed.example/cover", "image/png", 100L,
                Instant.EPOCH);
        FundingStoryContext context = new FundingStoryContext(project, List.of(reward), List.of(sourceImage));
        SessionResponse session = new SessionResponse(sessionId, 2, 1,
                List.of(new ChatMessage("assistant", "질문")), List.of(),
                new StorySummary("제품", "이야기", List.of(new StrengthSummary("강점", "설명"))), chatId);
        AsyncError error = new AsyncError("E", "오류", true, null);
        GeneratedBody body = new GeneratedBody("hero", List.of(
                new GeneratedContentBlock("IMAGE", null, "hero"),
                new GeneratedContentBlock("TEXT", "본문", null)));
        SuccessfulImage image = new SuccessfulImage("hero", "https://file.example/hero.png",
                "image/png", 100L, 10, 10);
        FailedSlot failed = new FailedSlot("body", "generation", error);
        UploadTarget target = new UploadTarget("hero", "https://upload.example", "https://file.example/hero.png",
                Instant.EPOCH);

        List<Object> contracts = List.of(
                category,
                project,
                option,
                reward,
                sourceImage,
                context,
                new SessionCreateRequest(context),
                new PublicSessionCreateRequest(),
                new ChatMessage("user", "메시지"),
                new StrengthSummary("강점", "설명"),
                new StorySummary("제품", "이야기", List.of()),
                session,
                new LatestSessionResponse(session),
                new ChatAcceptedResponse(chatId, "accepted"),
                new MessageRequest("message-1", 2, "답변"),
                new ConfirmRequest(2),
                new ConfirmResponse(sessionId, 2),
                new PublicRunCreateRequest(sessionId, 2, "run-key"),
                new RunCreateRequest(sessionId, 2, "run-key", context),
                new RunAcceptedResponse(runId, "queued"),
                new OutputDescriptor("hero", "hero.png", "image/png", 100L),
                new UploadTargetsRequest(List.of(new OutputDescriptor("hero", "hero.png", "image/png", 100L))),
                target,
                new UploadTargetsResponse(List.of(target)),
                error,
                new GeneratedContentBlock("TEXT", "본문", null),
                body,
                image,
                failed,
                new RunCompletionRequest("partially_succeeded", body, List.of(image), List.of(failed), null),
                new RunCompletionResponse(runId, "succeeded"),
                new PublicContentBlock("TEXT", "본문"),
                new PublicStoryResult("https://file.example/hero.png", List.of(new PublicContentBlock("TEXT", "본문"))),
                new PublicRunResponse(runId, "succeeded", new PublicStoryResult(null, List.of()), List.of(), null));

        assertThat(contracts).hasSize(34).allSatisfy(contract -> assertThat(contract).isNotNull());
        assertThat(session.session_id()).isEqualTo(sessionId);
        assertThat(new PublicRunCreateRequest(sessionId, 2, "run-key").idempotency_key()).isEqualTo("run-key");
    }
}
