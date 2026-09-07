package com.fundit.project.application.reward;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionGroupJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionGroupJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionValueJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionValueJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardQueryServiceUnitTest {

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

    private Project publicProject(UUID publicId) {
        return Project.builder()
                .id(1L).publicId(publicId).sellerId(UUID.randomUUID()).status(ProjectStatus.ONGOING)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void 잔여재고를_조회할_수_없으면_null이고_품절이_아니다() {
        // given
        UUID publicId = UUID.randomUUID();
        RewardJpaEntity reward = RewardJpaEntity.builder()
                .id(1L).projectId(1L).name("얼리버드").description("설명").price(39000L)
                .isLimited(true).quantity(100).isEarlyBird(true).hasOption(false).sortOrder(0)
                .simpleRefundDisabled(false).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(publicProject(publicId)));
        when(rewardJpaRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(1L)).thenReturn(List.of(reward));
        when(inventoryQueryClient.getRemainingStock(1L)).thenReturn(Optional.empty());

        // when
        var result = rewardQueryService.listForConsumer(publicId);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).remainingStock()).isNull();
        assertThat(result.get(0).soldOut()).isFalse();
    }

    @Test
    void 잔여재고가_0이면_품절이다() {
        // given
        UUID publicId = UUID.randomUUID();
        RewardJpaEntity reward = RewardJpaEntity.builder()
                .id(1L).projectId(1L).name("얼리버드").description("설명").price(39000L)
                .isLimited(true).quantity(100).isEarlyBird(true).hasOption(false).sortOrder(0)
                .simpleRefundDisabled(false).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(publicProject(publicId)));
        when(rewardJpaRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(1L)).thenReturn(List.of(reward));
        when(inventoryQueryClient.getRemainingStock(1L)).thenReturn(Optional.of(0));

        // when
        var result = rewardQueryService.listForConsumer(publicId);

        // then
        assertThat(result.get(0).soldOut()).isTrue();
    }

    @Test
    void 옵션이_있으면_그룹과_값을_함께_반환한다() {
        // given
        UUID publicId = UUID.randomUUID();
        RewardJpaEntity reward = RewardJpaEntity.builder()
                .id(1L).projectId(1L).name("얼리버드").description("설명").price(39000L)
                .isLimited(false).isEarlyBird(false).hasOption(true).sortOrder(0)
                .simpleRefundDisabled(false).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        RewardOptionGroupJpaEntity group = RewardOptionGroupJpaEntity.builder().id(10L).rewardId(1L).name("색상").sortOrder(0).build();
        RewardOptionValueJpaEntity value = RewardOptionValueJpaEntity.builder().id(100L).optionGroupId(10L).value("화이트").sortOrder(0).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(publicProject(publicId)));
        when(rewardJpaRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(1L)).thenReturn(List.of(reward));
        when(inventoryQueryClient.getRemainingStock(1L)).thenReturn(Optional.empty());
        when(optionGroupJpaRepository.findByRewardIdAndDeletedAtIsNullOrderBySortOrderAsc(1L)).thenReturn(List.of(group));
        when(optionValueJpaRepository.findByOptionGroupIdOrderBySortOrderAsc(10L)).thenReturn(List.of(value));

        // when
        var result = rewardQueryService.listForConsumer(publicId);

        // then
        assertThat(result.get(0).options()).hasSize(1);
        assertThat(result.get(0).options().get(0).values().get(0).value()).isEqualTo("화이트");
    }

    @Test
    void 고시정보_목록을_조회한다() {
        // given
        UUID publicId = UUID.randomUUID();
        RewardJpaEntity reward = RewardJpaEntity.builder()
                .id(1L).projectId(1L).name("얼리버드").description("설명").price(39000L)
                .isLimited(false).isEarlyBird(false).hasOption(false).sortOrder(0)
                .categoryType("COSMETIC").disclosure(Map.of("제조국", "대한민국"))
                .simpleRefundDisabled(false).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(publicProject(publicId)));
        when(rewardJpaRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(1L)).thenReturn(List.of(reward));

        // when
        var result = rewardQueryService.listDisclosures(publicId);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).disclosure()).containsEntry("제조국", "대한민국");
    }
}
