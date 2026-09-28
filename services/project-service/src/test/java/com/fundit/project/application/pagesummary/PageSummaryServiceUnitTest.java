package com.fundit.project.application.pagesummary;

import com.fundit.project.application.ai.FundingStoryAiClient;
import com.fundit.project.application.ai.FundingStoryAiContracts.ArtifactError;
import com.fundit.project.application.ai.FundingStoryAiContracts.ArtifactOutput;
import com.fundit.project.application.ai.FundingStoryAiContracts.ArtifactView;
import com.fundit.project.application.ai.FundingStoryAiContracts.PageSummaryRunCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.PageSummaryRunResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.ProjectSnapshot;
import com.fundit.project.application.ai.FundingStoryAiContracts.SummarySection;
import com.fundit.project.application.ai.FundingStoryContextFactory;
import com.fundit.project.domain.pagesummary.PageSummary;
import com.fundit.project.domain.pagesummary.PageSummaryRepository;
import com.fundit.project.domain.pagesummary.PageSummarySection;
import com.fundit.project.domain.pagesummary.PageSummaryStatus;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.reward.RewardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageSummaryServiceUnitTest {

    private static final Long PROJECT_ID = 1L;
    private static final UUID PUBLIC_ID = UUID.randomUUID();
    private static final UUID RUN_ID = UUID.randomUUID();
    private static final ProjectSnapshot SNAPSHOT = new ProjectSnapshot("제목", "테크/가전", List.of(), List.of());

    @Mock
    PageSummaryRepository pageSummaryRepository;
    @Mock
    ProjectRepository projectRepository;
    @Mock
    RewardRepository rewardRepository;
    @Mock
    FundingStoryContextFactory contextFactory;
    @Mock
    FundingStoryAiClient aiClient;

    @InjectMocks
    PageSummaryService service;

    private final Project project = Project.builder()
            .id(PROJECT_ID).publicId(PUBLIC_ID).status(ProjectStatus.ONGOING).title("제목").build();

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
    }

    @Nested
    class 입력이_바뀌었을_때 {

        @Test
        void 해시가_같으면_AI를_부르지_않고_확인만_한다() {
            // given
            PageSummary summary = dirty().contentHash("same").sourceRevision(1).build();
            given(summary);
            when(contextFactory.pageSummaryHash(any(), any())).thenReturn("same");

            // when
            service.process(PROJECT_ID);

            // then
            verifyNoInteractions(aiClient);
            assertThat(saved().isDirty()).isFalse();
            assertThat(saved().getSourceRevision()).isEqualTo(1);
        }

        @Test
        void 해시가_다르면_새_revision으로_요청하고_이전_결과를_내린다() {
            // given
            PageSummary summary = dirty().contentHash("old").sourceRevision(1).attempt(2)
                    .status(PageSummaryStatus.SUCCEEDED)
                    .sections(List.of(new PageSummarySection("WHAT", "h", "d"))).build();
            given(summary);
            when(contextFactory.pageSummaryHash(any(), any())).thenReturn("new");
            when(contextFactory.pageSummarySnapshot(any(), any())).thenReturn(SNAPSHOT);
            when(aiClient.createPageSummaryRun(eq(PUBLIC_ID), any())).thenReturn(run(null));

            // when
            service.process(PROJECT_ID);

            // then
            PageSummaryRunCreateRequest request = createdRequest();
            assertThat(request.source_revision()).isEqualTo(2);
            assertThat(request.idempotency_key()).isEqualTo(PUBLIC_ID + ":2:1");
            assertThat(request.trigger()).isEqualTo("PROJECT_CONTENT_UPDATED");
            assertThat(saved().getStatus()).isEqualTo(PageSummaryStatus.REQUESTED);
            assertThat(saved().getSections()).isNull();
            assertThat(saved().getRunId()).isEqualTo(RUN_ID);
        }

        @Test
        void 첫_요청은_등록_완료_trigger로_보낸다() {
            // given
            given(dirty().build());
            when(contextFactory.pageSummaryHash(any(), any())).thenReturn("h");
            when(contextFactory.pageSummarySnapshot(any(), any())).thenReturn(SNAPSHOT);
            when(aiClient.createPageSummaryRun(eq(PUBLIC_ID), any())).thenReturn(run(null));

            // when
            service.process(PROJECT_ID);

            // then
            assertThat(createdRequest().trigger()).isEqualTo("PROJECT_REGISTRATION_COMPLETED");
            assertThat(createdRequest().idempotency_key()).isEqualTo(PUBLIC_ID + ":1:1");
        }
    }

    @Nested
    class 결과를_폴링할_때 {

        @Test
        void 성공하면_WHAT_WHY_두_절을_저장한다() {
            // given
            given(requested().build());
            when(aiClient.getPageSummaryRun(PUBLIC_ID, RUN_ID)).thenReturn(run(new ArtifactView("SUCCEEDED",
                    new ArtifactOutput(List.of(new SummarySection("WHAT", "무엇", "설명"),
                            new SummarySection("WHY", "왜", "설명"))), null)));

            // when
            service.process(PROJECT_ID);

            // then
            assertThat(saved().isSucceeded()).isTrue();
            assertThat(saved().getSections()).extracting(PageSummarySection::role).containsExactly("WHAT", "WHY");
        }

        @Test
        void 길이_제한을_넘는_결과는_저장하지_않는다() {
            // given
            given(requested().build());
            when(aiClient.getPageSummaryRun(PUBLIC_ID, RUN_ID)).thenReturn(run(new ArtifactView("SUCCEEDED",
                    new ArtifactOutput(List.of(new SummarySection("WHAT", "가".repeat(121), "설명"),
                            new SummarySection("WHY", "왜", "설명"))), null)));

            // when
            service.process(PROJECT_ID);

            // then
            assertThat(saved().getStatus()).isEqualTo(PageSummaryStatus.FAILED);
            assertThat(saved().getErrorCode()).isEqualTo("INVALID_OUTPUT");
        }

        @Test
        void 서명_URL이_만료되면_같은_revision에_새_멱등키로_다시_접수한다() {
            // given
            given(requested().build());
            when(aiClient.getPageSummaryRun(PUBLIC_ID, RUN_ID)).thenReturn(run(new ArtifactView("FAILED", null,
                    new ArtifactError("IMAGE_READ_URL_EXPIRED", false, "expired"))));
            when(contextFactory.pageSummarySnapshot(any(), any())).thenReturn(SNAPSHOT);
            when(aiClient.createPageSummaryRun(eq(PUBLIC_ID), any())).thenReturn(run(null));

            // when
            service.process(PROJECT_ID);

            // then
            assertThat(createdRequest().source_revision()).isEqualTo(1);
            assertThat(createdRequest().idempotency_key()).isEqualTo(PUBLIC_ID + ":1:2");
            assertThat(saved().getAttempt()).isEqualTo(2);
            assertThat(saved().getStatus()).isEqualTo(PageSummaryStatus.REQUESTED);
        }

        @Test
        void 재시도_가능한_실패는_두_번까지만_재시도한다() {
            // given
            ArtifactView retryable = new ArtifactView("FAILED", null, new ArtifactError("MODEL_TIMEOUT", true, null));
            given(requested().retryCount(1).build());
            when(aiClient.getPageSummaryRun(PUBLIC_ID, RUN_ID)).thenReturn(run(retryable));

            // when — 두 번째 실패: 60초 대기
            service.process(PROJECT_ID);

            // then
            assertThat(saved().getStatus()).isEqualTo(PageSummaryStatus.RETRY_WAIT);
            assertThat(saved().getRetryCount()).isEqualTo(2);
            assertThat(saved().getNextAttemptAt()).isAfter(Instant.now().plusSeconds(50));

            // when — 대기 후 retry 호출, 세 번째 실패는 닫는다
            service.process(PROJECT_ID);
            verify(aiClient).retryPageSummaryRun(PUBLIC_ID, RUN_ID);
            service.process(PROJECT_ID);

            // then
            assertThat(saved().getStatus()).isEqualTo(PageSummaryStatus.FAILED);
            assertThat(saved().getErrorCode()).isEqualTo("MODEL_TIMEOUT");
        }

        @Test
        void 제한시간이_지나면_AI를_부르지_않고_실패로_닫는다() {
            // given
            given(requested().requestedAt(Instant.now().minusSeconds(31 * 60)).build());

            // when
            service.process(PROJECT_ID);

            // then
            verify(aiClient, never()).getPageSummaryRun(any(), any());
            assertThat(saved().getErrorCode()).isEqualTo("TIMEOUT");
        }
    }

    @Test
    void 공개_프로젝트만_요약_대상으로_표시한다() {
        // given
        Project draft = project.toBuilder().status(ProjectStatus.DRAFT).build();

        // when
        service.markDirtyIfPublic(draft);
        service.markDirtyIfPublic(project);

        // then
        verify(pageSummaryRepository).markDirty(eq(PROJECT_ID), any());
    }

    private PageSummary.PageSummaryBuilder dirty() {
        return PageSummary.builder().projectId(PROJECT_ID).dirtyAt(Instant.now());
    }

    private PageSummary.PageSummaryBuilder requested() {
        Instant now = Instant.now();
        return PageSummary.builder().projectId(PROJECT_ID).dirtyAt(now.minusSeconds(10)).handledDirtyAt(now.minusSeconds(10))
                .contentHash("h").sourceRevision(1).attempt(1).runId(RUN_ID)
                .status(PageSummaryStatus.REQUESTED).nextAttemptAt(now).requestedAt(now);
    }

    /** 같은 객체를 계속 돌려줘 process를 여러 번 불러도 상태가 이어진다. */
    private void given(PageSummary summary) {
        when(pageSummaryRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(summary));
        when(pageSummaryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PageSummary saved() {
        ArgumentCaptor<PageSummary> captor = ArgumentCaptor.forClass(PageSummary.class);
        verify(pageSummaryRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private PageSummaryRunCreateRequest createdRequest() {
        ArgumentCaptor<PageSummaryRunCreateRequest> captor = ArgumentCaptor.forClass(PageSummaryRunCreateRequest.class);
        verify(aiClient).createPageSummaryRun(eq(PUBLIC_ID), captor.capture());
        return captor.getValue();
    }

    private static PageSummaryRunResponse run(ArtifactView pageSummary) {
        return new PageSummaryRunResponse(RUN_ID, "QUEUED", 1,
                pageSummary == null ? Map.of() : Map.of("PAGE_SUMMARY", pageSummary));
    }
}
