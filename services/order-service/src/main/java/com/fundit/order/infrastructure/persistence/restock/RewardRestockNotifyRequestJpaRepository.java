package com.fundit.order.infrastructure.persistence.restock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RewardRestockNotifyRequestJpaRepository extends JpaRepository<RewardRestockNotifyRequestJpaEntity, Long> {

    boolean existsByRewardIdAndMemberId(Long rewardId, UUID memberId);

    /** 재입고(ORDER-016) 시 대기 신청자 전원에게 알림을 보내기 위한 조회. */
    List<RewardRestockNotifyRequestJpaEntity> findByRewardId(Long rewardId);
}
