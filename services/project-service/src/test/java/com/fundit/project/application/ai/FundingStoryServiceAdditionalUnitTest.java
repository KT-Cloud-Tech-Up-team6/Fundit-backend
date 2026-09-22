package com.fundit.project.application.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.project.application.ai.FundingStoryAiClient.ChatEventStream;
import com.fundit.project.application.ai.FundingStoryAiContracts.AsyncError;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingStoryServiceAdditionalUnitTest {

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
    void 최신_세션과_채팅_관련_계약을_소유권_검증_후_AI에_중계한다() throws Exception {
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

        assertThat(fundingStoryAiServiceLatest(sellerId, projectId).session()).isEqualTo(sessionResponse);
        assertThat(fundingStoryAiClientGetSession(sellerId, projectId, sessionId)).isEqualTo(sessionResponse);
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

        assertThat(fundingStoryService.confirmSession(sellerId, projectId, sessionId, new ConfirmRequest(4)))
                .isEqualTo(response);
        assertThat(session.getCoreFingerprint()).isEqualTo("new");
        verify(sessionRepository).save(session);
    }

    @Test
    void 확인된_Core로_전체생성을_등록하고_run_추적자를_만든다() {
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

        assertThat(fundingStoryService.createRun(sellerId, projectId, request)).isEqualTo(response);
        ArgumentCaptor<FundingStorySession> saved = ArgumentCaptor.forClass(FundingStorySession.class);
        verify(sessionRepository).save(saved.capture());
        assertThat(saved.getValue().isRunTracker()).isTrue();
        verify(fundingStoryAiClient).createRun(eq(projectId), any());
    }

    @Test
    void run_조회는_대기중_완료_결과없음과_소유권_오류를_구분한다() {
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = project(sellerId, projectId, ProjectStatus.DRAFT);
        UUID queuedId = UUID.randomUUID();
        UUID completedId = UUID.randomUUID();
        UUID emptyId = UUID.randomUUID();
        UUID forbiddenId = UUID.randomUUID();
        FundingStorySession queued = FundingStorySession.trackRun(queuedId, project.getId(), sellerId, UUID.randomUUID());
        FundingStorySession completed = FundingStorySession.trackRun(completedId, project.getId(), sellerId, UUID.randomUUID());
        completed.finishRun(new FundingStoryResult("succeeded", "https://file/cover.png",
                List.of(new IntroContentBlock(com.fundit.project.domain.project.IntroContentType.TEXT, "본문")),
                List.of(new FundingStoryFailedSlot("optional", "generation",
                        new FundingStoryRunError("OPTIONAL", "선택 슬롯 실패", true, null))), null));
        FundingStorySession emptyResult = FundingStorySession.trackRun(emptyId, project.getId(), sellerId, UUID.randomUUID())
                .toBuilder().status(FundingStorySessionStatus.COMPLETED).result(null).build();
        FundingStorySession forbidden = FundingStorySession.trackRun(forbiddenId, project.getId(), UUID.randomUUID(), UUID.randomUUID());

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(queuedId)).thenReturn(Optional.of(queued));
        when(sessionRepository.findById(completedId)).thenReturn(Optional.of(completed));
        when(sessionRepository.findById(emptyId)).thenReturn(Optional.of(emptyResult));
        when(sessionRepository.findById(forbiddenId)).thenReturn(Optional.of(forbidden));

        assertThat(fundingStoryService.getRun(sellerId, projectId, queuedId).status()).isEqualTo("queued");
        PublicRunResponse completedResponse = fundingStoryService.getRun(sellerId, projectId, completedId);
        assertThat(completedResponse.status()).isEqualTo("succeeded");
        assertThat(completedResponse.result().intro_content().get(0).value()).isEqualTo("본문");
        assertThat(completedResponse.failed_slots()).hasSize(1);
        assertThat(fundingStoryService.getRun(sellerId, projectId, emptyId).error().code())
                .isEqualTo("RESULT_NOT_AVAILABLE");
        assertThatThrownBy(() -> fundingStoryService.getRun(sellerId, projectId, forbiddenId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void AI_이미지_업로드_대상을_발급하고_잘못된_요청을_거부한다() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project(null, projectId, ProjectStatus.DRAFT)));
        when(storageClient.presignPut(any(), eq("image/png"), any(Duration.class)))
                .thenReturn(new MediaStorageClient.PresignedUpload("https://upload", "https://file"));

        var response = fundingStoryService.createUploadTargets(projectId,
                new UploadTargetsRequest(List.of(
                        new OutputDescriptor("hero", "hero.png", "image/png", 100L),
                        new OutputDescriptor("body", "body.png", "image/png", 200L))));

        assertThat(response.targets()).extracting("slot_id").containsExactly("hero", "body");
        assertThat(response.targets()).allSatisfy(target -> assertThat(target.expires_at()).isNotNull());
        assertThatThrownBy(() -> fundingStoryService.createUploadTargets(projectId,
                new UploadTargetsRequest(List.of(new OutputDescriptor("hero", "hero.jpg", "image/jpeg", 100L)))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 실패_callback은_실패_상태로_저장하고_프로젝트는_수정하지_않는다() {
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

        RunCompletionResponse response = fundingStoryService.completeRun(projectId, runId, request);

        assertThat(response.status()).isEqualTo("failed");
        assertThat(run.getStatus()).isEqualTo(FundingStorySessionStatus.FAILED);
        verify(sessionRepository).save(run);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void 공개_프로젝트에_성공_callback이_오면_스토리와_색인_이벤트를_갱신한다() {
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

        assertThat(fundingStoryService.completeRun(projectId, runId, request).status()).isEqualTo("succeeded");
        assertThat(project.getCoverImageUrl()).isEqualTo(url);
        verify(projectIndexEventPublisher).publishProjectUpdated(any(ProjectIndexEventPublisher.ProjectIndexedEvent.class));
    }

    @Test
    void completion_형태가_계약과_다르면_입력오류로_거부한다() {
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Project project = project(UUID.randomUUID(), projectId, ProjectStatus.DRAFT);
        FundingStorySession run = FundingStorySession.trackRun(runId, project.getId(), project.getSellerId(), UUID.randomUUID());

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(runId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> fundingStoryService.completeRun(projectId, runId,
                new RunCompletionRequest("succeeded", null, List.of(), List.of(), null)))
                .isInstanceOf(BusinessException.class);
    }

    private LatestSessionResponse fundingStoryAiServiceLatest(UUID sellerId, UUID projectId) {
        return fundingStoryService.getLatestSession(sellerId, projectId);
    }

    private SessionResponse fundingStoryAiClientGetSession(UUID sellerId, UUID projectId, UUID sessionId) {
        return fundingStoryService.getSession(sellerId, projectId, sessionId);
    }

    private Project project(UUID sellerId, UUID publicId, ProjectStatus status) {
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
