package com.fundit.project.application.reward;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionGroupJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionGroupJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionValueJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 리워드/옵션 조회 및 재고 확인, 법정고시정보 조회(소비자) — PROJECT-027, PROJECT-028. */
@Service
@RequiredArgsConstructor
public class RewardQueryService {

    private final ProjectRepository projectRepository;
    private final RewardJpaRepository rewardJpaRepository;
    private final RewardOptionGroupJpaRepository optionGroupJpaRepository;
    private final RewardOptionValueJpaRepository optionValueJpaRepository;
    private final InventoryQueryClient inventoryQueryClient;

    @Transactional(readOnly = true)
    public List<RewardConsumerView> listForConsumer(UUID projectPublicId) {
        Long projectId = loadPublicProjectId(projectPublicId);
        return rewardJpaRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(projectId).stream()
                .map(this::toConsumerView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RewardDisclosureView> listDisclosures(UUID projectPublicId) {
        Long projectId = loadPublicProjectId(projectPublicId);
        return rewardJpaRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(projectId).stream()
                .map(r -> new RewardDisclosureView(r.getId(), r.getName(), r.getCategoryType(), r.getDisclosure()))
                .toList();
    }

    private RewardConsumerView toConsumerView(RewardJpaEntity reward) {
        Integer remainingStock = inventoryQueryClient.getRemainingStock(reward.getId()).orElse(null);
        boolean soldOut = remainingStock != null && remainingStock <= 0;

        List<RewardOptionGroupView> options = reward.getHasOption()
                ? optionGroupJpaRepository.findByRewardIdAndDeletedAtIsNullOrderBySortOrderAsc(reward.getId()).stream()
                        .map(this::toGroupView)
                        .toList()
                : List.of();

        return new RewardConsumerView(reward.getId(), reward.getRewardDisplayCode(), reward.getName(), reward.getPrice(),
                reward.getIsEarlyBird(), reward.getIsLimited(), remainingStock, options, soldOut);
    }

    private RewardOptionGroupView toGroupView(RewardOptionGroupJpaEntity group) {
        var values = optionValueJpaRepository.findByOptionGroupIdOrderBySortOrderAsc(group.getId()).stream()
                .map(v -> new RewardOptionValueView(v.getId(), v.getValue()))
                .toList();
        return new RewardOptionGroupView(group.getId(), group.getName(), values);
    }

    private Long loadPublicProjectId(UUID projectPublicId) {
        Project project = projectRepository.findByPublicId(projectPublicId)
                .filter(Project::isPublic)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return project.getId();
    }

    public record RewardOptionValueView(Long valueId, String value) {
    }

    public record RewardOptionGroupView(Long groupId, String groupName, List<RewardOptionValueView> values) {
    }

    public record RewardConsumerView(
            Long rewardId, String rewardDisplayCode, String name, Long price, boolean isEarlyBird,
            boolean isLimited, Integer remainingStock, List<RewardOptionGroupView> options, boolean soldOut) {
    }

    public record RewardDisclosureView(Long rewardId, String rewardName, String categoryType, Map<String, String> disclosure) {
    }
}
