package com.fundit.project.application.funding;

import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.application.port.FundingPort;
import com.fundit.project.application.port.MemberPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.infrastructure.persistence.engagement.OpenNotifyRequestJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("FundingSummaryService")
@ExtendWith(MockitoExtension.class)
class FundingSummaryServiceUnitTest {

    @Mock
    private ProjectAccessGuard accessGuard;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private FundingStatsReader fundingStatsReader;
    @Mock
    private MemberPort memberPort;
    @Mock
    private OpenNotifyRequestJpaRepository openNotifyJpaRepository;

    @InjectMocks
    private FundingSummaryService fundingSummaryService;

    @BeforeEach
    void setUp() {
        CurrentUser seller = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
        when(currentUserProvider.require()).thenReturn(seller);
        when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.ongoing());
    }

    @Test
    void 집계값과_찜_오픈알림_수가_함께_담긴다() {
        // given
        when(fundingStatsReader.read(ProjectFixture.PROJECT_ID))
                .thenReturn(new FundingPort.FundingStats(3_200_000L, 128, List.of()));
        when(memberPort.countWishes(ProjectFixture.PROJECT_ID)).thenReturn(42L);
        when(openNotifyJpaRepository.countByProjectId(ProjectFixture.PROJECT_ID)).thenReturn(17L);

        // when
        var summary = fundingSummaryService.find(ProjectFixture.PUBLIC_ID);

        // then
        assertThat(summary.stats().currentAmount()).isEqualTo(3_200_000L);
        assertThat(summary.wishCount()).isEqualTo(42L);
        assertThat(summary.openNotifyCount()).isEqualTo(17L);
    }

    @Test
    void 구매자_상세조회와_같은_캐시를_거친다() {
        // given
        when(fundingStatsReader.read(ProjectFixture.PROJECT_ID))
                .thenReturn(FundingPort.FundingStats.empty());
        when(memberPort.countWishes(ProjectFixture.PROJECT_ID)).thenReturn(0L);
        when(openNotifyJpaRepository.countByProjectId(ProjectFixture.PROJECT_ID)).thenReturn(0L);

        // when
        var summary = fundingSummaryService.find(ProjectFixture.PUBLIC_ID);

        // then — FundingPort를 직접 부르면 두 화면의 최신성이 어긋난다
        assertThat(summary.stats().currentAmount()).isZero();
    }
}
