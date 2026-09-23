package com.fundit.project.infrastructure.persistence.reward;

import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardOptionGroup;
import com.fundit.project.domain.reward.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /**
     * {@code group.id()}가 이 리워드의 기존 그룹과 일치하면 그 그룹 행을 그대로 두고 이름/값만
     * 갱신한다(ID 유지) — 그 외(신규 그룹, 또는 다른 리워드 소속이라 이 리워드의 기존 그룹
     * 목록에 없는 ID)는 새 그룹으로 취급한다. 요청에 없는 기존 그룹은 삭제한다. 값은 그룹
     * 단위로 항상 통째로 교체한다(개별 값 ID를 참조하는 소비자가 없어 그룹 단위 안정성이면 충분).
     */
    @Override
    @Transactional
    public List<RewardOptionGroup> replaceOptions(Long rewardId, List<RewardOptionGroup> optionGroups) {
        if (rewardJpaRepository.findByIdForUpdate(rewardId).isEmpty()) {
            return List.of();
        }
        Map<Long, RewardOptionGroupJpaEntity> existingById = optionGroupJpaRepository.findByRewardId(rewardId).stream()
                .collect(Collectors.toMap(RewardOptionGroupJpaEntity::getId, Function.identity()));

        Set<Long> keptGroupIds = new HashSet<>();
        List<RewardOptionGroup> persisted = new ArrayList<>();
        int groupSortOrder = 0;
        for (RewardOptionGroup group : optionGroups) {
            RewardOptionGroupJpaEntity existing = group.id() == null ? null : existingById.get(group.id());
            Long groupId = existing == null
                    ? insertGroup(rewardId, group.groupName(), groupSortOrder++)
                    : updateGroup(existing, group.groupName(), groupSortOrder++);
            if (existing != null) {
                keptGroupIds.add(groupId);
                optionValueJpaRepository.deleteByOptionGroupId(groupId);
            }
            replaceValues(groupId, group.values());
            persisted.add(new RewardOptionGroup(groupId, group.groupName(), group.values()));
        }

        for (RewardOptionGroupJpaEntity existing : existingById.values()) {
            if (!keptGroupIds.contains(existing.getId())) {
                optionValueJpaRepository.deleteByOptionGroupId(existing.getId());
                optionGroupJpaRepository.delete(existing);
            }
        }
        return persisted;
    }

    private Long insertGroup(Long rewardId, String name, int sortOrder) {
        return optionGroupJpaRepository.save(RewardOptionGroupJpaEntity.builder()
                .rewardId(rewardId)
                .name(name)
                .sortOrder(sortOrder)
                .build()).getId();
    }

    /**
     * merge()로 갱신되므로 @PrePersist가 돌지 않는다 — createdAt은 유지한다. updatedAt은 설정하지
     * 않아도 된다: trg_reward_option_groups_updated_at이 UPDATE 시 항상 덮어쓴다.
     */
    private Long updateGroup(RewardOptionGroupJpaEntity existing, String name, int sortOrder) {
        return optionGroupJpaRepository.save(RewardOptionGroupJpaEntity.builder()
                .id(existing.getId())
                .rewardId(existing.getRewardId())
                .name(name)
                .sortOrder(sortOrder)
                .createdAt(existing.getCreatedAt())
                .updatedAt(existing.getUpdatedAt())
                .build()).getId();
    }

    private void replaceValues(Long groupId, List<String> values) {
        int valueSortOrder = 0;
        for (String value : values) {
            optionValueJpaRepository.save(RewardOptionValueJpaEntity.builder()
                    .optionGroupId(groupId)
                    .value(value)
                    .sortOrder(valueSortOrder++)
                    .build());
        }
    }

    @Override
    public Optional<Reward> findById(Long id) {
        return rewardJpaRepository.findByIdAndDeletedAtIsNull(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Reward> findByProjectIdAndIdempotencyKey(Long projectId, String idempotencyKey) {
        return rewardJpaRepository.findByProjectIdAndIdempotencyKeyAndDeletedAtIsNull(projectId, idempotencyKey)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Reward> findByIdForUpdate(Long id) {
        return rewardJpaRepository.findByIdForUpdate(id)
                .filter(entity -> entity.getDeletedAt() == null)
                .map(mapper::toDomain);
    }

    @Override
    public List<Reward> findByProjectId(Long projectId) {
        return rewardJpaRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(projectId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
