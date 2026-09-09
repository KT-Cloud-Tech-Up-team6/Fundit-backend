package com.fundit.payment.application.settlement;

import com.fundit.payment.infrastructure.persistence.settlement.SettlementScheduleJpaEntity;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementScheduleJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SettlementScheduleServiceUnitTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Mock
    private SettlementScheduleJpaRepository settlementScheduleJpaRepository;

    private SettlementScheduleService settlementScheduleService;

    @BeforeEach
    void setUp() {
        settlementScheduleService = new SettlementScheduleService(settlementScheduleJpaRepository);
    }

    @Test
    void 펀딩_성공이면_5영업일_뒤_선정산_스케줄을_등록한다() {
        // given — 금요일 달성이면 주말을 건너 다음주 금요일이 5영업일
        Instant friday = ZonedDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZONE).toInstant();
        UUID sellerId = UUID.randomUUID();
        var event = new FundingSucceededListener.FundingSucceededEvent(1024L, 10L, sellerId, friday);

        // when
        settlementScheduleService.onFundingSucceeded(event);

        // then
        ArgumentCaptor<SettlementScheduleJpaEntity> captor = ArgumentCaptor.forClass(SettlementScheduleJpaEntity.class);
        verify(settlementScheduleJpaRepository).save(captor.capture());
        SettlementScheduleJpaEntity saved = captor.getValue();
        assertThat(saved.getFundingId()).isEqualTo(1024L);
        assertThat(saved.getProjectId()).isEqualTo(10L);
        assertThat(saved.getSellerId()).isEqualTo(sellerId);
        assertThat(saved.getBatchType()).isEqualTo(SettlementScheduleJpaEntity.TYPE_INTERIM);
        assertThat(saved.getDueAt().atZone(ZONE).getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(saved.getDueAt().atZone(ZONE).toLocalDate()).isEqualTo(
                ZonedDateTime.of(2026, 9, 11, 10, 0, 0, 0, ZONE).toLocalDate());
    }

    @Test
    void 배송완료면_14일_뒤_최종정산_스케줄을_등록한다() {
        // given
        Instant completedAt = Instant.parse("2026-09-01T00:00:00Z");
        UUID sellerId = UUID.randomUUID();
        var event = new ShippingCompletionListener.ShippingCompletedEvent(2048L, 10L, sellerId, completedAt);

        // when
        settlementScheduleService.onShippingCompleted(event);

        // then
        ArgumentCaptor<SettlementScheduleJpaEntity> captor = ArgumentCaptor.forClass(SettlementScheduleJpaEntity.class);
        verify(settlementScheduleJpaRepository).save(captor.capture());
        SettlementScheduleJpaEntity saved = captor.getValue();
        assertThat(saved.getBatchType()).isEqualTo(SettlementScheduleJpaEntity.TYPE_FINAL);
        assertThat(saved.getDueAt()).isEqualTo(completedAt.plus(java.time.Duration.ofDays(14)));
        assertThat(saved.getFundingId()).isEqualTo(2048L);
    }
}
