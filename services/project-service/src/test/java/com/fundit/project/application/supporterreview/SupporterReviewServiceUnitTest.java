package com.fundit.project.application.supporterreview;

import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.application.port.FundingPort;
import com.fundit.project.application.port.MemberPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.infrastructure.persistence.supporterreview.SupporterReviewJpaEntity;
import com.fundit.project.infrastructure.persistence.supporterreview.SupporterReviewJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("SupporterReviewService")
@ExtendWith(MockitoExtension.class)
class SupporterReviewServiceUnitTest {

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

    @Nested
    class 목록_조회 {

        @Test
        void 작성자_닉네임이_붙는다() {
            // given
            when(currentUserProvider.find()).thenReturn(Optional.empty());
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, null)).thenReturn(ProjectFixture.ongoing());
            when(reviewJpaRepository.findByProjectIdOrderByCreatedAtDesc(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(SupporterReviewJpaEntity.builder()
                            .id(1L)
                            .projectId(ProjectFixture.PROJECT_ID)
                            .memberId(ProjectFixture.OTHER_MEMBER_ID)
                            .content("잘 받았습니다")
                            .build()));
            when(memberPort.findNicknames(List.of(ProjectFixture.OTHER_MEMBER_ID)))
                    .thenReturn(Map.of(ProjectFixture.OTHER_MEMBER_ID, "서포터A"));

            // when
            var reviews = supporterReviewService.list(ProjectFixture.PUBLIC_ID);

            // then
            assertThat(reviews).hasSize(1);
            assertThat(reviews.getFirst().nickname()).isEqualTo("서포터A");
        }
    }

    @Nested
    class 작성 {

        @Test
        void 배송이_완료된_본인_펀딩이면_등록된다() {
            // given
            CurrentUser member = new CurrentUser(ProjectFixture.OTHER_MEMBER_ID, Role.MEMBER);
            when(currentUserProvider.require()).thenReturn(member);
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, ProjectFixture.OTHER_MEMBER_ID))
                    .thenReturn(ProjectFixture.ongoing());
            when(fundingPort.checkReviewEligibility(FUNDING_ID, ProjectFixture.OTHER_MEMBER_ID))
                    .thenReturn(new FundingPort.ReviewEligibility(true, true));
            when(reviewJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            var saved = supporterReviewService.create(ProjectFixture.PUBLIC_ID, FUNDING_ID, "잘 받았습니다");

            // then
            assertThat(saved.getFundingId()).isEqualTo(FUNDING_ID);
            assertThat(saved.getMemberId()).isEqualTo(ProjectFixture.OTHER_MEMBER_ID);
            assertThat(saved.getContent()).isEqualTo("잘 받았습니다");
        }
    }
}
