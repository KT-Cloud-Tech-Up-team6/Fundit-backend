package com.fundit.payment.application.settlement;

import com.fundit.payment.infrastructure.persistence.settlement.SettlementHoldJpaEntity;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementHoldJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * settlement.settlement_holds(에스크로 보류 금액) 관리 — 단순 애그리거트라
 * persistence-convention.md §2에 따라 application이 JpaRepository를 직접 쓴다.
 * PAYMENT-002(결제 완료)에서 보류를 열고, 환불 완료 시 RELEASED_TO_REFUND로,
 * 정산 배치 편입 시 RELEASED_TO_SETTLEMENT로 해제한다.
 */
@Service
@RequiredArgsConstructor
public class SettlementHoldService {

    private final SettlementHoldJpaRepository settlementHoldJpaRepository;

    @Transactional
    public void openHold(UUID paymentId, Long fundingId, long amount) {
        settlementHoldJpaRepository.save(SettlementHoldJpaEntity.builder()
                .paymentId(paymentId)
                .fundingId(fundingId)
                .holdAmount(amount)
                .build());
    }

    @Transactional
    public void releaseToRefund(UUID paymentId) {
        release(paymentId, SettlementHoldJpaEntity.STATUS_RELEASED_TO_REFUND);
    }

    @Transactional
    public void releaseToSettlement(UUID paymentId) {
        release(paymentId, SettlementHoldJpaEntity.STATUS_RELEASED_TO_SETTLEMENT);
    }

    private void release(UUID paymentId, String newStatus) {
        settlementHoldJpaRepository.findByPaymentId(paymentId).ifPresent(hold -> {
            if (hold.isHolding()) {
                hold.release(newStatus);
                settlementHoldJpaRepository.save(hold);
            }
        });
    }
}
