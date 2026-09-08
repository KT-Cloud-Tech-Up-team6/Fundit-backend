package com.fundit.order.infrastructure.persistence.restock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RewardRestockNotifyRequestJpaRepository extends JpaRepository<RewardRestockNotifyRequestJpaEntity, Long> {

    boolean existsByRewardIdAndMemberId(Long rewardId, UUID memberId);
}
