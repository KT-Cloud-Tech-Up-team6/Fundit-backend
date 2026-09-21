package com.fundit.order.infrastructure.event;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.order.application.funding.FundingRewardStatsPublisher.RewardStatItem;
import com.fundit.order.application.funding.FundingRewardStatsPublisher.RewardStatsUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaFundingRewardStatsEventTransportUnitExceptionTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private KafkaFundingRewardStatsEventTransport transport;

    @Test
    void 전송이_실패하면_예외가_발생한다() {
        // given
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("브로커 확인 응답 실패")));

        // when & then
        assertThatThrownBy(() -> transport.send(
                new RewardStatsUpdatedEvent(1L, List.of(new RewardStatItem(2L, 3L, 1, 1000L))), 9L))
                .isInstanceOf(DependencyFailureException.class);
    }
}
