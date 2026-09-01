package com.fundit.project.application.reward;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.FundingPort;
import com.fundit.project.application.port.InventoryPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardOption;
import com.fundit.project.domain.reward.RewardOptionRepository;
import com.fundit.project.domain.reward.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RewardOptionService {

    private final RewardRepository rewardRepository;
    private final RewardOptionRepository rewardOptionRepository;
    private final ProjectAccessGuard accessGuard;
    private final CurrentUserProvider currentUserProvider;
    private final InventoryPort inventoryPort;
    private final FundingPort fundingPort;

    /**
     * PROJECT-011. 재고 수량은 이 서비스에 저장하지 않고 order-service에 초기화를 위임한다.
     */
    public RewardOption create(UUID projectId, Long rewardId, String optionName, String sku, Integer initialStock) {
        Reward reward = ownedReward(projectId, rewardId);

        if (initialStock == null || initialStock < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "초기 재고는 0개 이상이어야 합니다.");
        }
        if (rewardOptionRepository.existsBySku(sku)) {
            throw new BusinessException(ProjectErrorCode.DUPLICATE_SKU);
        }

        RewardOption saved = saveWithSkuGuard(reward, optionName, sku);
        inventoryPort.initialize(saved.getId(), saved.getSku(), initialStock);
        return saved;
    }

    /**
     * 위 중복 확인과 INSERT 사이에 다른 요청이 끼어들면 유니크 인덱스에서 걸린다.
     * 그대로 두면 500이 나가므로, 같은 원인인 이상 조회로 걸렀을 때와 같은 409로 맞춘다.
     */
    private RewardOption saveWithSkuGuard(Reward reward, String optionName, String sku) {
        try {
            return rewardOptionRepository.save(RewardOption.create(reward.getId(), optionName, sku));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ProjectErrorCode.DUPLICATE_SKU);
        }
    }

    /** PROJECT-012. sku와 재고는 여기서 바꿀 수 없다(재고는 order-service 책임). */
    public RewardOption rename(UUID projectId, Long rewardId, Long optionId, String optionName) {
        Reward reward = ownedReward(projectId, rewardId);
        RewardOption option = findOptionOf(reward, optionId);
        option.rename(optionName);
        return rewardOptionRepository.save(option);
    }

    /** PROJECT-013. 삭제 후 order-service에 재고 비활성화를 알린다. */
    public void delete(UUID projectId, Long rewardId, Long optionId) {
        Reward reward = ownedReward(projectId, rewardId);
        RewardOption option = findOptionOf(reward, optionId);

        if (fundingPort.hasFundingForOption(option.getId())) {
            throw new BusinessException(ProjectErrorCode.REWARD_HAS_ACTIVE_FUNDING);
        }

        option.softDelete(Instant.now());
        rewardOptionRepository.save(option);
        inventoryPort.deactivate(option.getId());
    }

    private Reward ownedReward(UUID projectId, Long rewardId) {
        var currentUser = currentUserProvider.require();
        Project project = accessGuard.findOwned(projectId, currentUser);
        project.ensureModifiable();

        Reward reward = rewardRepository.findActiveById(rewardId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!reward.getProjectId().equals(project.getId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return reward;
    }

    private RewardOption findOptionOf(Reward reward, Long optionId) {
        RewardOption option = rewardOptionRepository.findActiveById(optionId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!option.getRewardId().equals(reward.getId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return option;
    }
}
