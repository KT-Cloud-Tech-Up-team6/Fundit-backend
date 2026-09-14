package com.fundit.order.infrastructure.persistence.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FundingEventOutboxJpaEntityUnitTest {

    @Test
    void createdAt이_없으면_생성_시점으로_채워진다() {
        // given
        FundingEventOutboxJpaEntity entity = FundingEventOutboxJpaEntity.builder()
                .eventType(FundingEventOutboxJpaEntity.TYPE_SUCCEEDED)
                .fundingId(1024L)
                .projectId(123L)
                .build();

        // when
        entity.onCreate();

        // then
        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    void createdAt이_이미_있으면_덮어쓰지_않는다() {
        // given
        Instant fixed = Instant.parse("2026-01-01T00:00:00Z");
        FundingEventOutboxJpaEntity entity = FundingEventOutboxJpaEntity.builder()
                .eventType(FundingEventOutboxJpaEntity.TYPE_SUCCEEDED)
                .fundingId(1024L)
                .projectId(123L)
                .createdAt(fixed)
                .build();

        // when
        entity.onCreate();

        // then
        assertThat(entity.getCreatedAt()).isEqualTo(fixed);
    }
}
