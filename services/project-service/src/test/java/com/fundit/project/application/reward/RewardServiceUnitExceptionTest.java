package com.fundit.project.application.reward;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardRepository;
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
class RewardServiceUnitExceptionTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private RewardRepository rewardRepository;
    @Mock
    private RewardEventPublisher rewardEventPublisher;

    @InjectMocks
    private RewardService rewardService;

    @Test
    void 존재하지_않는_프로젝트에_리워드를_등록하면_404_예외가_발생한다() {
        // given
        UUID projectPublicId = UUID.randomUUID();
        when(projectRepository.findByPublicId(projectPublicId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> rewardService.create(UUID.randomUUID(), projectPublicId,
                new RewardService.CreateRewardCommand("이름", "설명", null, 1000L, false, null, false, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 타인_소유_프로젝트에_리워드를_등록하면_403_예외가_발생한다() {
        // given
        UUID projectPublicId = UUID.randomUUID();
        Project project = Project.builder()
                .id(1L).publicId(projectPublicId).sellerId(UUID.randomUUID()).status(ProjectStatus.DRAFT)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(projectPublicId)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> rewardService.create(UUID.randomUUID(), projectPublicId,
                new RewardService.CreateRewardCommand("이름", "설명", null, 1000L, false, null, false, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    @Test
    void 존재하지_않는_리워드를_수정하면_404_예외가_발생한다() {
        // given
        when(rewardRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> rewardService.update(UUID.randomUUID(), 99L,
                new RewardService.UpdateRewardCommand(null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 타인_소유_리워드를_수정하면_403_예외가_발생한다() {
        // given
        Reward existing = Reward.create(1L, "이름", "설명", null, 1000L, false, null, false, null)
                .toBuilder().id(5L).build();
        Project project = Project.builder()
                .id(1L).publicId(UUID.randomUUID()).sellerId(UUID.randomUUID()).status(ProjectStatus.DRAFT)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(rewardRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> rewardService.update(UUID.randomUUID(), 5L,
                new RewardService.UpdateRewardCommand(null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }
}
