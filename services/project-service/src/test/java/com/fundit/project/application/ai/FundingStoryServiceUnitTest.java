package com.fundit.project.application.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.ai.FundingStoryAiContracts.CategoryFact;
import com.fundit.project.application.ai.FundingStoryAiContracts.FundingStoryContext;
import com.fundit.project.application.ai.FundingStoryAiContracts.GeneratedBody;
import com.fundit.project.application.ai.FundingStoryAiContracts.GeneratedContentBlock;
import com.fundit.project.application.ai.FundingStoryAiContracts.ProjectFact;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicRunCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicRunResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCompletionRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCompletionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.SessionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.SuccessfulImage;
import com.fundit.project.application.media.MediaStorageClient;
import com.fundit.project.application.project.ProjectIndexEventPublisher;
import com.fundit.project.application.project.SellerProfileClient;
import com.fundit.project.domain.aifundingstory.FundingStorySession;
import com.fundit.project.domain.aifundingstory.FundingStorySessionRepository;
import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.reward.RewardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingStoryServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private RewardRepository rewardRepository;
    @Mock
    private FundingStorySessionRepository sessionRepository;
    @Mock
    private FundingStoryAiClient fundingStoryAiClient;
    @Mock
    private FundingStoryContextFactory contextFactory;
    @Mock
    private MediaStorageClient storageClient;
    @Mock
    private ProjectIndexEventPublisher projectIndexEventPublisher;
    @Mock
    private SellerProfileClient sellerProfileClient;

    @InjectMocks
    private FundingStoryService fundingStoryService;

    @Test
    void 세션을_생성하면_AI_ID와_Core_fingerprint를_기존_테이블에_추적한다() {
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Project project = ownedProject(sellerId, projectId);
        FundingStoryContext context = new FundingStoryContext(
                new ProjectFact("SOLE", new CategoryFact("테크", "가전"), "프로젝트", 1_000_000L),
                List.of(), List.of());
        SessionResponse aiResponse = new SessionResponse(
                sessionId, 1, null, List.of(), List.of("story"), null, null);

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(rewardRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(contextFactory.create(project, List.of())).thenReturn(context);
        when(contextFactory.fingerprint(project, List.of())).thenReturn("fingerprint");
        when(fundingStoryAiClient.createSession(projectId, context)).thenReturn(aiResponse);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        when(sessionRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        SessionResponse response = fundingStoryService.createSession(sellerId, projectId);

        assertThat(response).isEqualTo(aiResponse);
        ArgumentCaptor<FundingStorySession> saved = ArgumentCaptor.forClass(FundingStorySession.class);
        verify(sessionRepository).save(saved.capture());
        assertThat(saved.getValue().isSessionTracker()).isTrue();
        assertThat(saved.getValue().getCoreFingerprint()).isEqualTo("fingerprint");
    }

    @Test
    void 객체검증에_실패한_슬롯은_부분성공으로_낮추고_유효한_결과만_반영한다() {
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Project project = ownedProject(sellerId, projectId);
        FundingStorySession run = FundingStorySession.trackRun(runId, project.getId(), sellerId, UUID.randomUUID());
        String validUrl = "https://bucket/projects/" + projectId + "/ai/valid.png";
        String invalidUrl = "https://bucket/projects/" + projectId + "/ai/missing.png";
        RunCompletionRequest request = new RunCompletionRequest(
                "succeeded",
                new GeneratedBody("hero", List.of(
                        new GeneratedContentBlock("IMAGE", null, "hero"),
                        new GeneratedContentBlock("IMAGE", null, "missing"),
                        new GeneratedContentBlock("TEXT", "생성 본문", null))),
                List.of(
                        new SuccessfulImage("hero", validUrl, "image/png", 100L, 100, 100),
                        new SuccessfulImage("missing", invalidUrl, "image/png", 100L, 100, 100)),
                List.of(), null);

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(runId)).thenReturn(Optional.of(run));
        when(storageClient.extractKey(validUrl))
                .thenReturn(Optional.of("projects/" + projectId + "/ai/valid.png"));
        when(storageClient.extractKey(invalidUrl))
                .thenReturn(Optional.of("projects/" + projectId + "/ai/missing.png"));
        when(storageClient.headObject("projects/" + projectId + "/ai/valid.png"))
                .thenReturn(Optional.of(new MediaStorageClient.StoredObject(100L, "image/png")));
        when(storageClient.headObject("projects/" + projectId + "/ai/missing.png"))
                .thenReturn(Optional.empty());
        when(projectRepository.save(project)).thenReturn(project);
        when(sessionRepository.save(run)).thenReturn(run);

        RunCompletionResponse response = fundingStoryService.completeRun(projectId, runId, request);

        assertThat(response.status()).isEqualTo("partially_succeeded");
        assertThat(project.getCoverImageUrl()).isEqualTo(validUrl);
        assertThat(project.getIntroContent()).extracting(IntroContentBlock::value)
                .containsExactly(validUrl, "생성 본문");
        assertThat(run.getResult().failedSlots()).extracting("slotId").contains("missing");
        verify(projectRepository).save(project);
        verify(sessionRepository).save(run);
    }

    @Test
    void 확인_후_Core가_변경되면_전체생성에_재확인을_요구한다() {
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Project project = ownedProject(sellerId, projectId);
        FundingStorySession session = FundingStorySession.trackSession(
                sessionId, project.getId(), sellerId, "confirmed-fingerprint");

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(rewardRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(contextFactory.fingerprint(project, List.of())).thenReturn("changed-fingerprint");

        assertThatThrownBy(() -> fundingStoryService.createRun(
                sellerId, projectId, new PublicRunCreateRequest(sessionId, 2, "run-key")))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.CONFLICT);
                    assertThat(error.getDetail()).isEqualTo(Map.of("action", "reconfirm_summary"));
                });
    }

    @Test
    void 완료_callback이_제한시간을_넘기면_BE가_run을_실패로_종료한다() {
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Project project = ownedProject(sellerId, projectId);
        FundingStorySession run = FundingStorySession
                .trackRun(runId, project.getId(), sellerId, UUID.randomUUID())
                .toBuilder()
                .createdAt(Instant.now().minusSeconds(31 * 60L))
                .build();
        ReflectionTestUtils.setField(fundingStoryService, "runCallbackTimeoutMinutes", 30L);
        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(runId)).thenReturn(Optional.of(run));
        when(sessionRepository.save(run)).thenReturn(run);

        PublicRunResponse response = fundingStoryService.getRun(sellerId, projectId, runId);

        assertThat(response.status()).isEqualTo("failed");
        assertThat(response.error().code()).isEqualTo("RUN_CALLBACK_TIMEOUT");
        verify(sessionRepository).save(run);
    }

    private Project ownedProject(UUID sellerId, UUID publicId) {
        return Project.builder()
                .id(1L)
                .publicId(publicId)
                .sellerId(sellerId)
                .status(ProjectStatus.DRAFT)
                .title("프로젝트")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
