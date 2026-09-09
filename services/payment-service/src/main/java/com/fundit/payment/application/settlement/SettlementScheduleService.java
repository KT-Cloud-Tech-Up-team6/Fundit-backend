package com.fundit.payment.application.settlement;

import com.fundit.payment.infrastructure.persistence.settlement.SettlementScheduleJpaEntity;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementScheduleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * PAYMENT-013/014 — 정산 배치 실행 대상 등록. "달성확정일+5영업일"/"배송완료+14일" 시점을
 * 계산해 {@code settlement_schedule}에 적재하면, {@link SettlementScheduleWorker}가 주기적으로
 * 도래한 건을 꺼내 배치를 생성한다(payment-service CLAUDE.md "내부 큐 테이블" 설계).
 *
 * <p>[가정] "영업일"은 공휴일 캘린더 없이 토/일만 제외한 근사치다 — 공휴일까지 반영하려면
 * 별도 캘린더 데이터가 필요해 이번 구현 범위 밖으로 뒀다.
 */
@Service
@RequiredArgsConstructor
public class SettlementScheduleService implements FundingSucceededListener, ShippingCompletionListener {

    private static final int INTERIM_BUSINESS_DAYS = 5;
    private static final int FINAL_DAYS_AFTER_SHIPPING = 14;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final SettlementScheduleJpaRepository settlementScheduleJpaRepository;

    @Override
    @Transactional
    public void onFundingSucceeded(FundingSucceededEvent event) {
        Instant dueAt = addBusinessDays(event.achievedAt(), INTERIM_BUSINESS_DAYS);
        settlementScheduleJpaRepository.save(SettlementScheduleJpaEntity.builder()
                .fundingId(event.fundingId())
                .projectId(event.projectId())
                .sellerId(event.sellerId())
                .batchType(SettlementScheduleJpaEntity.TYPE_INTERIM)
                .dueAt(dueAt)
                .build());
    }

    @Override
    @Transactional
    public void onShippingCompleted(ShippingCompletedEvent event) {
        settlementScheduleJpaRepository.save(SettlementScheduleJpaEntity.builder()
                .fundingId(event.fundingId())
                .projectId(event.projectId())
                .sellerId(event.sellerId())
                .batchType(SettlementScheduleJpaEntity.TYPE_FINAL)
                .dueAt(event.completedAt().plus(java.time.Duration.ofDays(FINAL_DAYS_AFTER_SHIPPING)))
                .build());
    }

    private Instant addBusinessDays(Instant start, int businessDays) {
        ZonedDateTime date = start.atZone(ZONE);
        int added = 0;
        while (added < businessDays) {
            date = date.plusDays(1);
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return date.toInstant();
    }
}
