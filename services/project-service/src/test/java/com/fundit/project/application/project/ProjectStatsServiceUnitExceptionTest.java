package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaRepository;
import com.fundit.project.infrastructure.persistence.opennotify.ProjectOpenNotifyRequestJpaRepository;
import com.fundit.project.infrastructure.persistence.wishstats.ProjectWishStatJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectStatsServiceUnitExceptionTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private FundingStatusSnapshotJpaRepository fundingStatusSnapshotJpaRepository;
    @Mock
    private ProjectWishStatJpaRepository wishStatJpaRepository;
    @Mock
    private ProjectOpenNotifyRequestJpaRepository openNotifyRequestJpaRepository;

    @InjectMocks
    private ProjectStatsService projectStatsService;

    @Test
    void 타인_소유_프로젝트_펀딩현황조회는_403_예외가_발생한다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project project = Project.builder()
                .id(1L).publicId(publicId).sellerId(UUID.randomUUID()).status(ProjectStatus.ONGOING)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> projectStatsService.getFundingStatus(UUID.randomUUID(), publicId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    @Test
    void 존재하지_않는_프로젝트_찜통계조회는_404_예외가_발생한다() {
        // given
        UUID publicId = UUID.randomUUID();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> projectStatsService.getWishStats(UUID.randomUUID(), publicId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }
}
