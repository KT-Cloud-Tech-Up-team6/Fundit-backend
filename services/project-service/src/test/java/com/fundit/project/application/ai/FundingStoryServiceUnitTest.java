package com.fundit.project.application.ai;

import com.fundit.project.application.ai.FundingStoryAiClient.ChatEventStream;
import com.fundit.project.application.ai.FundingStoryAiContracts.AsyncError;
import com.fundit.project.application.ai.FundingStoryAiContracts.CategoryFact;
import com.fundit.project.application.ai.FundingStoryAiContracts.ChatAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.ConfirmRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.ConfirmResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.FundingStoryContext;
import com.fundit.project.application.ai.FundingStoryAiContracts.GeneratedBody;
import com.fundit.project.application.ai.FundingStoryAiContracts.GeneratedContentBlock;
import com.fundit.project.application.ai.FundingStoryAiContracts.LatestSessionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.MessageRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.OutputDescriptor;
import com.fundit.project.application.ai.FundingStoryAiContracts.ProjectFact;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicRunCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicRunResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCompletionRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCompletionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.SessionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.SuccessfulImage;
import com.fundit.project.application.ai.FundingStoryAiContracts.UploadTargetsRequest;
import com.fundit.project.application.media.MediaStorageClient;
import com.fundit.project.application.project.ProjectIndexEventPublisher;
import com.fundit.project.application.project.SellerProfileClient;
import com.fundit.project.domain.aifundingstory.FundingStoryFailedSlot;
import com.fundit.project.domain.aifundingstory.FundingStoryResult;
import com.fundit.project.domain.aifundingstory.FundingStoryRunError;
import com.fundit.project.domain.aifundingstory.FundingStorySession;
import com.fundit.project.domain.aifundingstory.FundingStorySessionRepository;
import com.fundit.project.domain.aifundingstory.FundingStorySessionStatus;
import com.fundit.project.domain.project.BusinessType;
import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.IntroContentType;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingStoryServiceUnitTest {

    @Mock
    ProjectRepository projectRepository;
    @Mock
    RewardRepository rewardRepository;
    @Mock
    FundingStorySessionRepository sessionRepository;
    @Mock
    FundingStoryAiClient fundingStoryAiClient;
    @Mock
    FundingStoryContextFactory contextFactory;
    @Mock
    MediaStorageClient storageClient;
    @Mock
    ProjectIndexEventPublisher projectIndexEventPublisher;
    @Mock
    SellerProfileClient sellerProfileClient;

    @InjectMocks
    private FundingStoryService fundingStoryService;

    @Test
    void 세션을_생성하면_AI_ID와_Core_fingerprint를_기존_테이블에_추적한다() {
        // given
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
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        SessionResponse response = fundingStoryService.createSession(sellerId, projectId);

        // then
        assertThat(response).isEqualTo(aiResponse);
        ArgumentCaptor<FundingStorySession> saved = ArgumentCaptor.forClass(FundingStorySession.class);
        verify(sessionRepository).save(saved.capture());
        assertThat(saved.getValue().isSessionTracker()).isTrue();
        assertThat(saved.getValue().getCoreFingerprint()).isEqualTo("fingerprint");
    }

    @Test
    void 객체검증에_실패한_슬롯은_부분성공으로_낮추고_유효한_결과만_반영한다() {
        // given
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

        // when
        RunCompletionResponse response = fundingStoryService.completeRun(projectId, runId, request);

        // then
        assertThat(response.status()).isEqualTo("partially_succeeded");
        assertThat(project.getCoverImageUrl()).isEqualTo(validUrl);
        assertThat(project.getIntroContent()).extracting(IntroContentBlock::value)
                .containsExactly(validUrl, "생성 본문");
        assertThat(run.getResult().failedSlots()).extracting("slotId").contains("missing");
        verify(projectRepository).save(project);
        verify(sessionRepository).save(run);
    }

    @Test
    void 완료_callback이_제한시간을_넘기면_BE가_run을_실패로_종료한다() {
        // given
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

        // when
        PublicRunResponse response = fundingStoryService.getRun(sellerId, projectId, runId);

        // then
        assertThat(response.status()).isEqualTo("failed");
        assertThat(response.error().code()).isEqualTo("RUN_CALLBACK_TIMEOUT");
        verify(sessionRepository).save(run);
    }

    @Test
    void 최신_세션과_채팅_관련_계약을_소유권_검증_후_AI에_중계한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        Project project = project(sellerId, projectId, ProjectStatus.DRAFT);
        FundingStorySession tracker = FundingStorySession.trackSession(sessionId, project.getId(), sellerId, "fingerprint");
        SessionResponse sessionResponse = new SessionResponse(sessionId, 1, null, List.of(), List.of("story"), null, chatId);
        ChatAcceptedResponse accepted = new ChatAcceptedResponse(chatId, "accepted");
        AtomicBoolean closed = new AtomicBoolean();

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(rewardRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(contextFactory.fingerprint(project, List.of())).thenReturn("fingerprint");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(tracker));
        when(fundingStoryAiClient.getLatestSession(projectId)).thenReturn(new LatestSessionResponse(sessionResponse));
        when(fundingStoryAiClient.getSession(projectId, sessionId)).thenReturn(sessionResponse);
        when(fundingStoryAiClient.startSession(projectId, sessionId)).thenReturn(accepted);
        when(fundingStoryAiClient.addMessage(eq(projectId), eq(sessionId), any(MessageRequest.class))).thenReturn(accepted);
        when(fundingStoryAiClient.openChatEvents(projectId, chatId)).thenReturn(new ChatEventStream(
                new ByteArrayInputStream("data: ready\n\n".getBytes(StandardCharsets.UTF_8)),
                () -> closed.set(true)));

        // when & then
        assertThat(fundingStoryService.getLatestSession(sellerId, projectId).session()).isEqualTo(sessionResponse);
        assertThat(fundingStoryService.getSession(sellerId, projectId, sessionId)).isEqualTo(sessionResponse);
        assertThat(fundingStoryService.startSession(sellerId, projectId, sessionId)).isEqualTo(accepted);
        assertThat(fundingStoryService.addMessage(sellerId, projectId, sessionId,
                new MessageRequest("message-1", 1, "답변"))).isEqualTo(accepted);

        try (ChatEventStream stream = fundingStoryService.openChatEvents(sellerId, projectId, chatId)) {
            assertThat(stream.body().readAllBytes()).containsExactly("data: ready\n\n".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(closed).isTrue();
    }

    @Test
    void 요약을_확인하면_최신_Core_fingerprint를_세션_추적자에_저장한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Project project = project(sellerId, projectId, ProjectStatus.DRAFT);
        FundingStorySession session = FundingStorySession.trackSession(sessionId, project.getId(), sellerId, "old");
        ConfirmResponse response = new ConfirmResponse(sessionId, 4);

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(fundingStoryAiClient.confirmSession(projectId, sessionId, new ConfirmRequest(4))).thenReturn(response);
        when(rewardRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(contextFactory.fingerprint(project, List.of())).thenReturn("new");
        when(sessionRepository.save(session)).thenReturn(session);

        // when
        var actual = fundingStoryService.confirmSession(sellerId, projectId, sessionId, new ConfirmRequest(4));

        // then
        assertThat(actual).isEqualTo(response);
        assertThat(session.getCoreFingerprint()).isEqualTo("new");
        verify(sessionRepository).save(session);
    }

    @Test
    void 확인된_Core로_전체생성을_등록하고_run_추적자를_만든다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Project project = project(sellerId, projectId, ProjectStatus.DRAFT);
        FundingStorySession session = FundingStorySession.trackSession(sessionId, project.getId(), sellerId, "fingerprint");
        FundingStoryContext context = new FundingStoryContext(
                new ProjectFact("SOLE", null, "프로젝트", 1_000_000L), List.of(), List.of());
        PublicRunCreateRequest request = new PublicRunCreateRequest(sessionId, 4, "run-key");
        RunAcceptedResponse response = new RunAcceptedResponse(runId, "queued");

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(rewardRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(contextFactory.fingerprint(project, List.of())).thenReturn("fingerprint");
        when(contextFactory.create(project, List.of())).thenReturn(context);
        when(fundingStoryAiClient.createRun(eq(projectId), any())).thenReturn(response);
        when(sessionRepository.findById(runId)).thenReturn(Optional.empty());
        when(sessionRepository.save(any(FundingStorySession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var actual = fundingStoryService.createRun(sellerId, projectId, request);

        // then
        assertThat(actual).isEqualTo(response);
        ArgumentCaptor<FundingStorySession> saved = ArgumentCaptor.forClass(FundingStorySession.class);
        verify(sessionRepository).save(saved.capture());
        assertThat(saved.getValue().isRunTracker()).isTrue();
        verify(fundingStoryAiClient).createRun(eq(projectId), any());
    }

    @Test
    void run_조회는_대기중_완료_결과없음_상태를_구분한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = project(sellerId, projectId, ProjectStatus.DRAFT);
        UUID queuedId = UUID.randomUUID();
        UUID completedId = UUID.randomUUID();
        UUID emptyId = UUID.randomUUID();
        FundingStorySession queued = FundingStorySession.trackRun(queuedId, project.getId(), sellerId, UUID.randomUUID());
        FundingStorySession completed = FundingStorySession.trackRun(completedId, project.getId(), sellerId, UUID.randomUUID());
        completed.finishRun(new FundingStoryResult("succeeded", "https://file/cover.png",
                List.of(new IntroContentBlock(IntroContentType.TEXT, "본문")),
                List.of(new FundingStoryFailedSlot("optional", "generation",
                        new FundingStoryRunError("OPTIONAL", "선택 슬롯 실패", true, null))), null));
        FundingStorySession emptyResult = FundingStorySession.trackRun(emptyId, project.getId(), sellerId, UUID.randomUUID())
                .toBuilder().status(FundingStorySessionStatus.COMPLETED).result(null).build();

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(queuedId)).thenReturn(Optional.of(queued));
        when(sessionRepository.findById(completedId)).thenReturn(Optional.of(completed));
        when(sessionRepository.findById(emptyId)).thenReturn(Optional.of(emptyResult));

        // when & then
        assertThat(fundingStoryService.getRun(sellerId, projectId, queuedId).status()).isEqualTo("queued");
        PublicRunResponse completedResponse = fundingStoryService.getRun(sellerId, projectId, completedId);
        assertThat(completedResponse.status()).isEqualTo("succeeded");
        assertThat(completedResponse.result().intro_content().get(0).value()).isEqualTo("본문");
        assertThat(completedResponse.failed_slots()).hasSize(1);
        assertThat(fundingStoryService.getRun(sellerId, projectId, emptyId).error().code())
                .isEqualTo("RESULT_NOT_AVAILABLE");
    }

    @Test
    void AI_이미지_업로드_대상을_발급한다() {
        // given
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project(null, projectId, ProjectStatus.DRAFT)));
        when(storageClient.presignPut(any(), eq("image/png"), any(Duration.class)))
                .thenReturn(new MediaStorageClient.PresignedUpload("https://upload", "https://file"));

        // when
        var response = fundingStoryService.createUploadTargets(projectId,
                new UploadTargetsRequest(List.of(
                        new OutputDescriptor("hero", "hero.png", "image/png", 100L),
                        new OutputDescriptor("body", "body.png", "image/png", 200L))));

        // then
        assertThat(response.targets()).extracting("slot_id").containsExactly("hero", "body");
        assertThat(response.targets()).allSatisfy(target -> assertThat(target.expires_at()).isNotNull());
    }

    @Test
    void 실패_callback은_실패_상태로_저장하고_프로젝트는_수정하지_않는다() {
        // given
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Project project = project(UUID.randomUUID(), projectId, ProjectStatus.DRAFT);
        FundingStorySession run = FundingStorySession.trackRun(runId, project.getId(), project.getSellerId(), UUID.randomUUID());
        RunCompletionRequest request = new RunCompletionRequest(
                "failed", null, List.of(),
                List.of(new FundingStoryAiContracts.FailedSlot("hero", "generation",
                        new AsyncError("IMAGE_FAILED", "이미지 생성 실패", true, null))),
                new AsyncError("GENERATION_FAILED", "생성 실패", true, Map.of("retry", true)));

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(runId)).thenReturn(Optional.of(run));
        when(sessionRepository.save(run)).thenReturn(run);

        // when
        RunCompletionResponse response = fundingStoryService.completeRun(projectId, runId, request);

        // then
        assertThat(response.status()).isEqualTo("failed");
        assertThat(run.getStatus()).isEqualTo(FundingStorySessionStatus.FAILED);
        verify(sessionRepository).save(run);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void 공개_프로젝트에_성공_callback이_오면_스토리와_색인_이벤트를_갱신한다() {
        // given
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Project project = project(UUID.randomUUID(), projectId, ProjectStatus.ONGOING);
        FundingStorySession run = FundingStorySession.trackRun(runId, project.getId(), project.getSellerId(), UUID.randomUUID());
        String url = "https://bucket/projects/" + projectId + "/ai/hero.png";
        RunCompletionRequest request = new RunCompletionRequest(
                "succeeded",
                new GeneratedBody("hero", List.of(new GeneratedContentBlock("IMAGE", null, "hero"),
                        new GeneratedContentBlock("TEXT", "본문", null))),
                List.of(new SuccessfulImage("hero", url, "image/png", 100L, 100, 100)),
                List.of(), null);

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(runId)).thenReturn(Optional.of(run));
        when(storageClient.extractKey(url)).thenReturn(Optional.of("projects/" + projectId + "/ai/hero.png"));
        when(storageClient.headObject("projects/" + projectId + "/ai/hero.png"))
                .thenReturn(Optional.of(new MediaStorageClient.StoredObject(100L, "image/png")));
        when(projectRepository.save(project)).thenReturn(project);
        when(sellerProfileClient.getDisplayName(project.getSellerId())).thenReturn(Optional.of("판매자"));
        when(sessionRepository.save(run)).thenReturn(run);

        // when
        var status = fundingStoryService.completeRun(projectId, runId, request).status();

        // then
        assertThat(status).isEqualTo("succeeded");
        assertThat(project.getCoverImageUrl()).isEqualTo(url);
        verify(projectIndexEventPublisher).publishProjectUpdated(any(ProjectIndexEventPublisher.ProjectIndexedEvent.class));
    }

    Project ownedProject(UUID sellerId, UUID publicId) {
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

    Project project(UUID sellerId, UUID publicId, ProjectStatus status) {
        return Project.builder()
                .id(1L)
                .publicId(publicId)
                .sellerId(sellerId)
                .businessType(BusinessType.SOLE)
                .categoryMajor("테크")
                .categoryMinor("가전")
                .title("프로젝트")
                .goalAmount(1_000_000L)
                .status(status)
                .build();
    }
}
