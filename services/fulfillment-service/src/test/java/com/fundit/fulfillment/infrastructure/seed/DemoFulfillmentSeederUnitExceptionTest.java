package com.fundit.fulfillment.infrastructure.seed;

import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaRepository;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaRepository;
import com.fundit.fulfillment.infrastructure.persistence.tracker.FulfillmentTrackerJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.tracker.FulfillmentTrackerJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoFulfillmentSeederUnitExceptionTest {

    @Mock private FulfillmentTrackerJpaRepository trackerRepository;
    @Mock private FulfillmentStageDetailJpaRepository stageDetailRepository;
    @Mock private FulfillmentScheduleChangeJpaRepository scheduleChangeRepository;
    @Mock private TransactionTemplate transactionTemplate;

    @SuppressWarnings("unchecked")
    @Test
    void 바스켓_시드가_실패해도_클린팟_클론은_넣는다() {
        // given — 대상마다 트랜잭션을 따로 연다. 첫 번째(바스켓)는 실패, 두 번째(클론)는 실제로 실행한다
        UUID clone = DemoFulfillmentSeeder.CLINPOT_CLONE.projectPublicId();
        willAnswer(inv -> {
            throw new IllegalStateException("db down");
        }).willAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
            return null;
        }).given(transactionTemplate).executeWithoutResult(any());
        given(trackerRepository.findByProjectPublicId(clone)).willReturn(Optional.empty());
        given(trackerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        new DemoFulfillmentSeeder(trackerRepository, stageDetailRepository, scheduleChangeRepository,
                transactionTemplate).run(null);

        // then
        ArgumentCaptor<FulfillmentTrackerJpaEntity> tracker = ArgumentCaptor.forClass(FulfillmentTrackerJpaEntity.class);
        verify(trackerRepository).save(tracker.capture());
        assertThat(tracker.getValue().getProjectPublicId()).isEqualTo(clone);
    }
}
