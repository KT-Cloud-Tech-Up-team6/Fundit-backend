package com.fundit.fulfillment.infrastructure.seed;

import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaRepository;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaRepository;
import com.fundit.fulfillment.infrastructure.persistence.tracker.FulfillmentTrackerJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.tracker.FulfillmentTrackerJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoFulfillmentSeederUnitTest {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Mock private FulfillmentTrackerJpaRepository trackerRepository;
    @Mock private FulfillmentStageDetailJpaRepository stageDetailRepository;
    @Mock private FulfillmentScheduleChangeJpaRepository scheduleChangeRepository;
    @Mock private TransactionTemplate transactionTemplate;

    private DemoFulfillmentSeeder seeder() {
        return new DemoFulfillmentSeeder(trackerRepository, stageDetailRepository, scheduleChangeRepository,
                transactionTemplate);
    }

    private static FulfillmentTrackerJpaEntity tracker(String stage) {
        return FulfillmentTrackerJpaEntity.builder()
                .id(3L).projectPublicId(DemoFulfillmentSeeder.DEMO_PROJECT_ID).currentStage(stage)
                .lastUpdatedAt(BASE.minus(Duration.ofDays(20))).createdAt(BASE.minus(Duration.ofDays(30))).build();
    }

    @SuppressWarnings("unchecked")
    private List<FulfillmentStageDetailJpaEntity> capturedDetails() {
        ArgumentCaptor<List<FulfillmentStageDetailJpaEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(stageDetailRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static Instant days(int days) {
        return BASE.plus(Duration.ofDays(days));
    }

    @Test
    void 처음이면_생산_중_트래커와_단계별_기록_일정_변경을_기준_시각으로_만든다() {
        // given
        given(trackerRepository.findByProjectPublicId(DemoFulfillmentSeeder.DEMO_PROJECT_ID)).willReturn(Optional.empty());
        given(trackerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        seeder().seed(BASE);

        // then — 마지막 기록이 7일 안이어야 미갱신 경고가 뜨지 않는다
        ArgumentCaptor<FulfillmentTrackerJpaEntity> tracker = ArgumentCaptor.forClass(FulfillmentTrackerJpaEntity.class);
        verify(trackerRepository).save(tracker.capture());
        assertThat(tracker.getValue().getCurrentStage()).isEqualTo("MANUFACTURING");
        assertThat(tracker.getValue().getLastUpdatedAt()).isEqualTo(days(-2));

        // 출고 계획 종료일이 미래여야 "발송 지연"이 되지 않는다
        List<FulfillmentStageDetailJpaEntity> details = capturedDetails();
        assertThat(details).hasSize(5);
        assertThat(details).filteredOn(d -> d.getStage().equals("SHIPPING_OUT"))
                .singleElement().extracting(FulfillmentStageDetailJpaEntity::getPlannedEndAt).isEqualTo(days(13));
        assertThat(details).filteredOn(d -> d.getStage().equals("MANUFACTURING"))
                .singleElement().satisfies(d -> {
                    assertThat(d.getUpdatedAt()).isEqualTo(days(-2));
                    assertThat(d.getPhotoUrls()).containsExactly("https://infrastudy.store/media/mock/demo-manufacturing-1.png");
                });

        ArgumentCaptor<FulfillmentScheduleChangeJpaEntity> change = ArgumentCaptor.forClass(FulfillmentScheduleChangeJpaEntity.class);
        verify(scheduleChangeRepository).save(change.capture());
        assertThat(change.getValue().getReasonType()).isEqualTo("STOCK_SHORTAGE");
        assertThat(change.getValue().getNewPlannedDate()).isEqualTo(days(7));
    }

    @Test
    void 시드_그대로면_날짜를_기동_시각_기준으로_다시_계산한다() {
        // given — 오래전에 넣은 시드. 그대로 두면 7일 경고·발송 지연이 뜬다
        given(trackerRepository.findByProjectPublicId(DemoFulfillmentSeeder.DEMO_PROJECT_ID))
                .willReturn(Optional.of(tracker("MANUFACTURING")));
        given(stageDetailRepository.findByTrackerIdOrderByUpdatedAtDesc(3L))
                .willReturn(Collections.nCopies(5, FulfillmentStageDetailJpaEntity.builder().build()));
        given(scheduleChangeRepository.findByTrackerIdOrderByChangedAtDesc(3L))
                .willReturn(List.of(FulfillmentScheduleChangeJpaEntity.builder().build()));

        // when
        seeder().seed(BASE);

        // then
        verify(stageDetailRepository).deleteAll(anyIterable());
        verify(scheduleChangeRepository).deleteAll(anyIterable());
        assertThat(capturedDetails()).hasSize(5);
        ArgumentCaptor<FulfillmentTrackerJpaEntity> tracker = ArgumentCaptor.forClass(FulfillmentTrackerJpaEntity.class);
        verify(trackerRepository).save(tracker.capture());
        assertThat(tracker.getValue().getId()).isEqualTo(3L);
        assertThat(tracker.getValue().getLastUpdatedAt()).isEqualTo(days(-2));
    }

    @Test
    void 시연_중_판매자가_바꿨으면_건드리지_않는다() {
        // given — 판매자가 검수 단계로 넘기고 기록을 하나 더 올렸다
        given(trackerRepository.findByProjectPublicId(DemoFulfillmentSeeder.DEMO_PROJECT_ID))
                .willReturn(Optional.of(tracker("INSPECTION")));
        given(stageDetailRepository.findByTrackerIdOrderByUpdatedAtDesc(3L))
                .willReturn(Collections.nCopies(6, FulfillmentStageDetailJpaEntity.builder().build()));
        given(scheduleChangeRepository.findByTrackerIdOrderByChangedAtDesc(3L))
                .willReturn(List.of(FulfillmentScheduleChangeJpaEntity.builder().build()));

        // when
        seeder().seed(BASE);

        // then
        verify(stageDetailRepository, never()).deleteAll(anyIterable());
        verify(stageDetailRepository, never()).saveAll(any());
        verify(trackerRepository, never()).save(any());
    }
}
