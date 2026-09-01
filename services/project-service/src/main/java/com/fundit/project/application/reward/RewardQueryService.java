package com.fundit.project.application.reward;

import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.InventoryPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardOption;
import com.fundit.project.domain.reward.RewardOptionRepository;
import com.fundit.project.domain.reward.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RewardQueryService {

    private final RewardRepository rewardRepository;
    private final RewardOptionRepository rewardOptionRepository;
    private final ProjectAccessGuard accessGuard;
    private final CurrentUserProvider currentUserProvider;
    private final InventoryPort inventoryPort;

    /** PROJECT-014. 재고는 order-service에서 조회해 병합하고, 삭제된 리워드/옵션은 제외한다. */
    public List<RewardWithOptions> listWithStock(UUID projectId) {
        Project project = visibleProject(projectId);

        List<Reward> rewards = rewardRepository.findActiveByProjectId(project.getId());
        List<Long> rewardIds = rewards.stream().map(Reward::getId).toList();
        List<RewardOption> options = rewardOptionRepository.findActiveByRewardIds(rewardIds);

        Map<Long, Integer> stocks = inventoryPort.findAvailableStocks(
                options.stream().map(RewardOption::getId).toList());

        return rewards.stream()
                .map(reward -> new RewardWithOptions(
                        reward,
                        options.stream()
                                .filter(option -> option.getRewardId().equals(reward.getId()))
                                .map(option -> toOptionStock(option, reward, stocks))
                                .toList()))
                .toList();
    }

    /** PROJECT-015. 전자상거래법 고시는 리워드 단위로 반복 노출한다. */
    public List<Reward> listDisclosures(UUID projectId) {
        Project project = visibleProject(projectId);
        return rewardRepository.findActiveByProjectId(project.getId());
    }

    /**
     * 재고를 조회하지 못한 옵션은 availableStock을 null로 두고 품절로 단정하지 않는다 —
     * 연동 실패를 품절로 표시하면 판매 가능한 옵션이 사라진 것처럼 보인다.
     */
    private OptionStock toOptionStock(RewardOption option, Reward reward, Map<Long, Integer> stocks) {
        if (reward.isUnlimited()) {
            return new OptionStock(option, null, false);
        }
        Integer stock = stocks.get(option.getId());
        return new OptionStock(option, stock, stock != null && stock <= 0);
    }

    private Project visibleProject(UUID projectId) {
        UUID viewerId = currentUserProvider.find()
                .map(CurrentUserProvider.CurrentUser::id)
                .orElse(null);
        return accessGuard.findVisible(projectId, viewerId);
    }

    public record RewardWithOptions(Reward reward, List<OptionStock> options) {
    }

    public record OptionStock(RewardOption option, Integer availableStock, boolean soldOut) {
    }
}
