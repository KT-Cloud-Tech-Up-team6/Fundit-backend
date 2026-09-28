package com.fundit.project.application.pagesummary;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.project.application.ai.FundingStoryAiClient;
import com.fundit.project.application.ai.FundingStoryContextFactory;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.pagesummary.PageSummary;
import com.fundit.project.domain.pagesummary.PageSummaryRepository;
import com.fundit.project.domain.pagesummary.PageSummaryStatus;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.reward.RewardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageSummaryServiceUnitExceptionTest {

    private static final Long PROJECT_ID = 1L;
    private static final UUID PUBLIC_ID = UUID.randomUUID();

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

    @BeforeEach
    void setUp() {
        PageSummary dirty = PageSummary.builder().projectId(PROJECT_ID).dirtyAt(Instant.now()).build();
        when(pageSummaryRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(dirty));
    }

    @Test
    void 본문_이미지를_읽을_수_없으면_AI를_부르지_않고_실패로_기록한다() {
        // given
        givenPublicProject();
        when(contextFactory.pageSummaryHash(any(), any())).thenReturn("h");
        when(contextFactory.pageSummarySnapshot(any(), any()))
                .thenThrow(new BusinessException(ProjectErrorCode.INVALID_PROJECT_DATA));

        // when
        service.process(PROJECT_ID);

        // then
        verifyNoInteractions(aiClient);
        assertThat(saved().getStatus()).isEqualTo(PageSummaryStatus.FAILED);
        assertThat(saved().getErrorCode()).isEqualTo("IMAGE_UNAVAILABLE");
    }

    @Test
    void AI_장애는_던져서_롤백하고_다음_주기에_다시_시도한다() {
        // given
        givenPublicProject();
        when(contextFactory.pageSummaryHash(any(), any())).thenReturn("h");
        when(aiClient.createPageSummaryRun(any(), any()))
                .thenThrow(new DependencyFailureException(new IllegalStateException("down")));

        // when & then
        assertThatThrownBy(() -> service.process(PROJECT_ID)).isInstanceOf(DependencyFailureException.class);
        verify(pageSummaryRepository, never()).save(any());
    }

    @Test
    void AI가_요청을_거절하면_반복하지_않고_실패로_닫는다() {
        // given
        givenPublicProject();
        when(contextFactory.pageSummaryHash(any(), any())).thenReturn("h");
        when(aiClient.createPageSummaryRun(any(), any())).thenThrow(new BusinessException(CommonErrorCode.CONFLICT));

        // when
        service.process(PROJECT_ID);

        // then
        assertThat(saved().getErrorCode()).isEqualTo("AI_REQUEST_REJECTED");
        assertThat(saved().isDirty()).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = CommonErrorCode.class, names = {"TOO_MANY_REQUESTS", "UNAUTHORIZED"})
    void AI_호출_제한이나_인증_오류는_실패로_닫지_않고_다음_주기에_다시_시도한다(CommonErrorCode code) {
        // given — 429는 일시 제한, 401은 토큰 설정 문제라 같은 요청이 나중에 성공할 수 있다
        givenPublicProject();
        when(contextFactory.pageSummaryHash(any(), any())).thenReturn("h");
        when(aiClient.createPageSummaryRun(any(), any())).thenThrow(new BusinessException(code));

        // when & then
        assertThatThrownBy(() -> service.process(PROJECT_ID)).isInstanceOf(BusinessException.class);
        verify(pageSummaryRepository, never()).save(any());
    }

    @Test
    void 공개가_아닌_프로젝트는_요청하지_않고_닫는다() {
        // given
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(
                Project.builder().id(PROJECT_ID).publicId(PUBLIC_ID).status(ProjectStatus.DRAFT).build()));

        // when
        service.process(PROJECT_ID);

        // then
        verifyNoInteractions(aiClient);
        assertThat(saved().getErrorCode()).isEqualTo("PROJECT_NOT_PUBLIC");
        assertThat(saved().isDirty()).isFalse();
    }

    private void givenPublicProject() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(
                Project.builder().id(PROJECT_ID).publicId(PUBLIC_ID).status(ProjectStatus.ONGOING).build()));
    }

    private PageSummary saved() {
        ArgumentCaptor<PageSummary> captor = ArgumentCaptor.forClass(PageSummary.class);
        verify(pageSummaryRepository).save(captor.capture());
        return captor.getValue();
    }
}
