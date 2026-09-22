package com.fundit.project.application.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.ai.FundingStoryAiContracts.OutputDescriptor;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicRunCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCompletionRequest;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}
