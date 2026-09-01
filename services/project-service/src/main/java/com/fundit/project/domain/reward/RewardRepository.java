package com.fundit.project.domain.reward;

import java.util.List;
import java.util.Optional;

public interface RewardRepository {

    Reward save(Reward reward);

    Optional<Reward> findActiveById(Long rewardId);

    List<Reward> findActiveByProjectId(Long projectId);

    boolean existsActiveByProjectId(Long projectId);
}
