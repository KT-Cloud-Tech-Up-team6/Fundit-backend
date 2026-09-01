package com.fundit.project.application.supporterreview;

import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.application.port.FundingPort;
import com.fundit.project.application.port.MemberPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.infrastructure.persistence.supporterreview.SupporterReviewJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.fundit.project.support.BusinessExceptionAssertions.assertBusinessException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SupporterReviewService 예외")
@ExtendWith(MockitoExtension.class)
class SupporterReviewServiceUnitExceptionTest {

    private static final Long FUNDING_ID = 7001L;

    @Mock
    private SupporterReviewJpaRepository reviewJpaRepository;
    @Mock
    private ProjectAccessGuard accessGuard;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private FundingPort fundingPort;
    @Mock
    private MemberPort memberPort;

    @InjectMocks
    private SupporterReviewService supporterReviewService;

    @BeforeEach
    void setUp() {
        CurrentUser member = new CurrentUser(ProjectFixture.OTHER_MEMBER_ID, Role.MEMBER);
        when(currentUserProvider.require()).thenReturn(member);
        when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, ProjectFixture.OTHER_MEMBER_ID))
                .thenReturn(ProjectFixture.ongoing());
    }

    @Test
    void 본인_펀딩이_아니면_권한_예외가_발생한다() {
        // given
        when(fundingPort.checkReviewEligibility(FUNDING_ID, ProjectFixture.OTHER_MEMBER_ID))
                .thenReturn(new FundingPort.ReviewEligibility(false, true));

        // when & then
        assertBusinessException(
                () -> supporterReviewService.create(ProjectFixture.PUBLIC_ID, FUNDING_ID, "후기"),
                CommonErrorCode.FORBIDDEN);
    }

    @Test
    void 배송이_완료되지_않았으면_처리불가_예외가_발생한다() {
        // given
        when(fundingPort.checkReviewEligibility(FUNDING_ID, ProjectFixture.OTHER_MEMBER_ID))
                .thenReturn(new FundingPort.ReviewEligibility(true, false));

        // when & then
        assertBusinessException(
                () -> supporterReviewService.create(ProjectFixture.PUBLIC_ID, FUNDING_ID, "후기"),
                ProjectErrorCode.SUPPORTER_REVIEW_NOT_ELIGIBLE);
    }

    @Test
    void 자격_검증에_실패하면_저장하지_않는다() {
        // given
        when(fundingPort.checkReviewEligibility(FUNDING_ID, ProjectFixture.OTHER_MEMBER_ID))
                .thenReturn(new FundingPort.ReviewEligibility(true, false));

        // when
        assertBusinessException(
                () -> supporterReviewService.create(ProjectFixture.PUBLIC_ID, FUNDING_ID, "후기"),
                ProjectErrorCode.SUPPORTER_REVIEW_NOT_ELIGIBLE);

        // then
        verify(reviewJpaRepository, never()).save(any());
    }
}
