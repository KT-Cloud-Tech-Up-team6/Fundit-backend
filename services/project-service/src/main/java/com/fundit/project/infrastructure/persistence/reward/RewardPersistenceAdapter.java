package com.fundit.project.infrastructure.persistence.reward;

import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RewardPersistenceAdapter implements RewardRepository {

    private final RewardJpaRepository jpaRepository;
    private final RewardMapper mapper;

    @Override
    public Reward save(Reward reward) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(reward)));
    }

    @Override
    public Optional<Reward> findActiveById(Long rewardId) {
        return jpaRepository.findByIdAndDeletedAtIsNull(rewardId).map(mapper::toDomain);
    }

    @Override
    public List<Reward> findActiveByProjectId(Long projectId) {
        return jpaRepository.findByProjectIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(projectId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsActiveByProjectId(Long projectId) {
        return jpaRepository.existsByProjectIdAndDeletedAtIsNull(projectId);
    }
}
