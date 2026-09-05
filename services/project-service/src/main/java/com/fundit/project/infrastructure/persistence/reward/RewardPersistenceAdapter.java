package com.fundit.project.infrastructure.persistence.reward;

import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardOptionGroup;
import com.fundit.project.domain.reward.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RewardPersistenceAdapter implements RewardRepository {

    private final RewardJpaRepository rewardJpaRepository;
    private final RewardOptionGroupJpaRepository optionGroupJpaRepository;
    private final RewardOptionValueJpaRepository optionValueJpaRepository;
    private final RewardMapper mapper;

    @Override
    public Reward save(Reward reward) {
        return mapper.toDomain(rewardJpaRepository.save(mapper.toEntity(reward)));
    }

    @Override
    @Transactional
    public void replaceOptions(Long rewardId, List<RewardOptionGroup> optionGroups) {
        for (RewardOptionGroupJpaEntity existingGroup : optionGroupJpaRepository.findByRewardId(rewardId)) {
            optionValueJpaRepository.deleteByOptionGroupId(existingGroup.getId());
        }
        optionGroupJpaRepository.deleteByRewardId(rewardId);

        int groupSortOrder = 0;
        for (RewardOptionGroup group : optionGroups) {
            RewardOptionGroupJpaEntity savedGroup = optionGroupJpaRepository.save(RewardOptionGroupJpaEntity.builder()
                    .rewardId(rewardId)
                    .name(group.groupName())
                    .sortOrder(groupSortOrder++)
                    .build());

            int valueSortOrder = 0;
            for (String value : group.values()) {
                optionValueJpaRepository.save(RewardOptionValueJpaEntity.builder()
                        .optionGroupId(savedGroup.getId())
                        .value(value)
                        .sortOrder(valueSortOrder++)
                        .build());
            }
        }
    }

    @Override
    public Optional<Reward> findById(Long id) {
        return rewardJpaRepository.findByIdAndDeletedAtIsNull(id).map(mapper::toDomain);
    }
}
