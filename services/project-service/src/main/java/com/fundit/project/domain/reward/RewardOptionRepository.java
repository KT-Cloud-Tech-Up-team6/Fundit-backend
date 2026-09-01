package com.fundit.project.domain.reward;

import java.util.List;
import java.util.Optional;

public interface RewardOptionRepository {

    RewardOption save(RewardOption option);

    Optional<RewardOption> findActiveById(Long optionId);

    List<RewardOption> findActiveByRewardId(Long rewardId);

    List<RewardOption> findActiveByRewardIds(List<Long> rewardIds);

    boolean existsBySku(String sku);
}
