package com.fundit.order.application.restock;

import com.fundit.order.infrastructure.persistence.restock.RewardRestockNotifyRequestJpaEntity;
import com.fundit.order.infrastructure.persistence.restock.RewardRestockNotifyRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** ORDER-011 — 재입고(품절) 알림 신청. (reward_id, member_id) upsert로 멱등 처리. */
@Service
@RequiredArgsConstructor
public class RestockNotifyService {

    private static final Logger log = LoggerFactory.getLogger(RestockNotifyService.class);

    private final RewardRestockNotifyRequestJpaRepository jpaRepository;

    @Transactional
    public void request(UUID memberId, Long rewardId) {
        if (jpaRepository.existsByRewardIdAndMemberId(rewardId, memberId)) {
            return; // 이미 신청됨 — 기존 신청 유지(idempotent)
        }
        try {
            jpaRepository.save(RewardRestockNotifyRequestJpaEntity.builder()
                    .rewardId(rewardId)
                    .memberId(memberId)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 동시에 중복 신청된 경쟁 상황 — 유니크 제약으로 걸러졌을 뿐 결과적으로는 신청된 상태이므로 무시.
            log.debug("재입고 알림 중복 신청(동시 요청) 무시. rewardId={}, memberId={}", rewardId, memberId);
        }
    }
}
