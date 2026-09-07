package com.fundit.project.application.reward;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionGroupJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionValueJpaRepository;
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
class RewardQueryServiceUnitExceptionTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private RewardJpaRepository rewardJpaRepository;
    @Mock
    private RewardOptionGroupJpaRepository optionGroupJpaRepository;
    @Mock
    private RewardOptionValueJpaRepository optionValueJpaRepository;
    @Mock
    private InventoryQueryClient inventoryQueryClient;

    @InjectMocks
    private RewardQueryService rewardQueryService;

    @Test
    void 비공개_프로젝트의_리워드_목록조회는_404를_반환한다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project project = Project.builder()
                .id(1L).publicId(publicId).sellerId(UUID.randomUUID()).status(ProjectStatus.DRAFT)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> rewardQueryService.listForConsumer(publicId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }
}
