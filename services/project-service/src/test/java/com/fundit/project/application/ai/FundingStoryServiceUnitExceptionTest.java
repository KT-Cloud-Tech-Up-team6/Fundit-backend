package com.fundit.project.application.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.ai.FundingStoryAiContracts.OutputDescriptor;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicMessageRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicAttachment;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicRunCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCompletionRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunDiscardRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.UploadTargetsRequest;
import com.fundit.project.application.media.MediaStorageClient;
import com.fundit.project.application.project.ProjectIndexEventPublisher;
import com.fundit.project.application.project.SellerProfileClient;
import com.fundit.project.domain.aifundingstory.FundingStorySession;
import com.fundit.project.domain.aifundingstory.FundingStorySessionRepository;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.reward.RewardRepository;
import com.fundit.project.infrastructure.content.RichTextSanitizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingStoryServiceUnitExceptionTest {

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
    @Spy
    private RichTextSanitizer richTextSanitizer = new RichTextSanitizer();

    @Mock

    private com.fundit.project.application.pagesummary.PageSummaryService pageSummaryService;


    @InjectMocks
    private FundingStoryService fundingStoryService;

    private final FundingStoryServiceUnitTest fixtures = new FundingStoryServiceUnitTest();

    @Test
    void 확인_후_Core가_변경되면_전체생성에_재확인을_요구한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Project project = fixtures.ownedProject(sellerId, projectId);
        FundingStorySession session = FundingStorySession.trackSession(
                sessionId, project.getId(), sellerId, "confirmed-fingerprint");

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(rewardRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(contextFactory.fingerprint(project, List.of())).thenReturn("changed-fingerprint");

        // when & then
        assertThatThrownBy(() -> fundingStoryService.createRun(
                sellerId, projectId, new PublicRunCreateRequest(sessionId, 2, "run-key")))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.CONFLICT);
                    assertThat(error.getDetail()).isEqualTo(Map.of("action", "reconfirm_summary"));
                });
    }

    @Test
    void completion_형태가_계약과_다르면_입력오류로_거부한다() {
        // given
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Project project = fixtures.project(UUID.randomUUID(), projectId, ProjectStatus.DRAFT);
        FundingStorySession run = FundingStorySession.trackRun(runId, project.getId(), project.getSellerId(), UUID.randomUUID());

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(runId)).thenReturn(Optional.of(run));

        // when & then
        assertThatThrownBy(() -> fundingStoryService.completeRun(projectId, runId,
                new RunCompletionRequest("succeeded", null, List.of(), List.of(), null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void run_조회는_소유자가_아니면_예외를_던진다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = fixtures.project(sellerId, projectId, ProjectStatus.DRAFT);
        UUID forbiddenId = UUID.randomUUID();
        FundingStorySession forbidden = FundingStorySession.trackRun(forbiddenId, project.getId(), UUID.randomUUID(), UUID.randomUUID());

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(forbiddenId)).thenReturn(Optional.of(forbidden));

        // when & then
        assertThatThrownBy(() -> fundingStoryService.getRun(sellerId, projectId, forbiddenId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 업로드_대상_요청의_콘텐츠타입이_잘못되면_거부한다() {
        // given
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByPublicId(projectId))
                .thenReturn(Optional.of(fixtures.project(null, projectId, ProjectStatus.DRAFT)));

        // when & then
        assertThatThrownBy(() -> fundingStoryService.createUploadTargets(projectId,
                new UploadTargetsRequest(List.of(new OutputDescriptor("hero", "hero.jpg", "image/jpeg", 100L)))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 폐기_요청에_run_ID와_idempotency_key가_모두_없으면_거부한다() {
        // when & then
        assertThatThrownBy(() -> fundingStoryService.discardRun(
                UUID.randomUUID(), UUID.randomUUID(), new RunDiscardRequest(null, " ")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    void 타_판매자의_run은_폐기할_수_없다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Project project = fixtures.project(sellerId, projectId, ProjectStatus.DRAFT);
        FundingStorySession run = FundingStorySession.trackRun(
                runId, project.getId(), UUID.randomUUID(), UUID.randomUUID(), "run-key");

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(runId)).thenReturn(Optional.of(run));

        // when & then
        assertThatThrownBy(() -> fundingStoryService.discardRun(
                sellerId, projectId, new RunDiscardRequest(runId, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    @Test
    void 같은_키로_다른_run이_진행_중이면_생성_등록은_CONFLICT다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Project project = fixtures.project(sellerId, projectId, ProjectStatus.DRAFT);
        FundingStorySession session = FundingStorySession.trackSession(
                sessionId, project.getId(), sellerId, "fingerprint");
        FundingStorySession other = FundingStorySession.trackRun(
                UUID.randomUUID(), project.getId(), sellerId, sessionId, "run-key");

        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(rewardRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(contextFactory.fingerprint(project, List.of())).thenReturn("fingerprint");
        when(fundingStoryAiClient.createRun(eq(projectId), any()))
                .thenReturn(new RunAcceptedResponse(runId, "queued"));
        when(sessionRepository.findById(runId)).thenReturn(Optional.empty());
        when(sessionRepository.insertIfKeyFree(any(FundingStorySession.class))).thenReturn(false);
        when(sessionRepository.findByProjectIdAndIdempotencyKey(project.getId(), "run-key"))
                .thenReturn(Optional.of(other));

        // when & then
        assertThatThrownBy(() -> fundingStoryService.createRun(
                sellerId, projectId, new PublicRunCreateRequest(sessionId, 4, "run-key")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);
    }

    @Test
    void 텍스트와_이미지가_모두_없으면_메시지를_거부한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Project project = fixtures.project(sellerId, projectId, ProjectStatus.DRAFT);
        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(
                FundingStorySession.trackSession(sessionId, project.getId(), sellerId, "fingerprint")));

        // when & then
        assertThatThrownBy(() -> fundingStoryService.addMessage(sellerId, projectId, sessionId,
                new PublicMessageRequest("m-1", 1, "  ", List.of())))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    void 첨부의_리워드가_이_프로젝트_리워드가_아니면_거부한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Project project = fixtures.project(sellerId, projectId, ProjectStatus.DRAFT);
        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(
                FundingStorySession.trackSession(sessionId, project.getId(), sellerId, "fingerprint")));
        when(rewardRepository.findByProjectId(project.getId())).thenReturn(List.of(Reward.builder().id(7L).build()));

        // when & then — 다른 프로젝트의 리워드 99
        assertThatThrownBy(() -> fundingStoryService.addMessage(sellerId, projectId, sessionId,
                new PublicMessageRequest("m-1", 1, "사진", List.of(new PublicAttachment("https://cdn/a.png", 99L)))))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }
}
