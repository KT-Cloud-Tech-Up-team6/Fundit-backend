package com.fundit.project.infrastructure.persistence.rewardoption;

import com.fundit.project.domain.reward.RewardOption;
import com.fundit.project.domain.reward.RewardOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RewardOptionPersistenceAdapter implements RewardOptionRepository {

    private final RewardOptionJpaRepository jpaRepository;
    private final RewardOptionMapper mapper;

    @Override
    public RewardOption save(RewardOption option) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(option)));
    }

    @Override
    public Optional<RewardOption> findActiveById(Long optionId) {
        return jpaRepository.findByIdAndDeletedAtIsNull(optionId).map(mapper::toDomain);
    }

    @Override
    public List<RewardOption> findActiveByRewardId(Long rewardId) {
        return jpaRepository.findByRewardIdAndDeletedAtIsNullOrderByIdAsc(rewardId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<RewardOption> findActiveByRewardIds(List<Long> rewardIds) {
        if (rewardIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByRewardIdInAndDeletedAtIsNullOrderByIdAsc(rewardIds).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsBySku(String sku) {
        return jpaRepository.existsBySku(sku);
    }
}
