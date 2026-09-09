package com.fundit.payment.application.settlement;

import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchItem;
import com.fundit.payment.domain.settlement.SettlementBatchRepository;
import com.fundit.payment.domain.settlement.SettlementBatchType;
import com.fundit.payment.domain.settlement.SettlementFeePolicy;
import com.fundit.payment.infrastructure.persistence.payment.PaymentCancellationJpaEntity;
import com.fundit.payment.infrastructure.persistence.payment.PaymentCancellationJpaRepository;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementScheduleJpaEntity;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementScheduleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PAYMENT-013/014 — settlement_schedule에서 도래한 건들을 판매자 단위로 묶어
 * settlement_batches(+items)를 생성한다. {@link SettlementScheduleWorker}가 주기적으로 호출한다.
 */
@Service
@RequiredArgsConstructor
public class SettlementBatchGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SettlementBatchGenerationService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentCancellationJpaRepository paymentCancellationJpaRepository;
    private final OrderSettlementAggregateClient orderSettlementAggregateClient;
    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementHoldService settlementHoldService;
    private final SettlementScheduleJpaRepository settlementScheduleJpaRepository;

    @Transactional
    public void generate(UUID sellerId, SettlementBatchType batchType, List<SettlementScheduleJpaEntity> entries) {
        List<SettlementBatchItem> items = new ArrayList<>();
        long gross = 0;
        long refundDeduction = 0;
        long couponDeduction = 0;
        Instant periodStart = null;

        for (SettlementScheduleJpaEntity entry : entries) {
            Optional<Payment> maybePayment = paymentRepository.findCompletedOrCancelledByFundingId(entry.getFundingId());
            if (maybePayment.isEmpty()) {
                log.warn("정산 대상 결제를 찾을 수 없어 건너뜁니다. fundingId={}", entry.getFundingId());
                entry.markProcessed();
                settlementScheduleJpaRepository.save(entry);
                continue;
            }
            Payment payment = maybePayment.get();
            long cancelledAmount = paymentCancellationJpaRepository.findByPaymentId(payment.getId()).stream()
                    .mapToLong(PaymentCancellationJpaEntity::getCancelAmount)
                    .sum();

            gross += payment.getAmount();
            refundDeduction += cancelledAmount;
            couponDeduction += orderSettlementAggregateClient.fetchMakerCouponDeductionAmount(entry.getFundingId());
            items.add(SettlementBatchItem.of(entry.getFundingId(), payment.getId(), payment.getAmount()));

            settlementHoldService.releaseToSettlement(payment.getId());
            entry.markProcessed();
            settlementScheduleJpaRepository.save(entry);

            if (periodStart == null || entry.getCreatedAt().isBefore(periodStart)) {
                periodStart = entry.getCreatedAt();
            }
        }

        if (items.isEmpty()) {
            return;
        }

        long platformFee = SettlementFeePolicy.calculatePlatformFee(gross);
        SettlementBatch batch = SettlementBatch.create(sellerId, batchType,
                periodStart == null ? Instant.now() : periodStart, Instant.now(),
                gross, platformFee, refundDeduction, couponDeduction, items);
        settlementBatchRepository.save(batch);
    }
}
