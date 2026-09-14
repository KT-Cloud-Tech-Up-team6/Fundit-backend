package com.fundit.order.infrastructure.persistence.restock;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RewardRestockNotifyRequestJpaEntityUnitTest {

    @Test
    void createdAt이_없으면_생성_시점으로_채워진다() {
        // given
        RewardRestockNotifyRequestJpaEntity entity = RewardRestockNotifyRequestJpaEntity.builder()
                .rewardId(1L)
                .memberId(UUID.randomUUID())
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
        RewardRestockNotifyRequestJpaEntity entity = RewardRestockNotifyRequestJpaEntity.builder()
                .rewardId(1L)
                .memberId(UUID.randomUUID())
                .createdAt(fixed)
                .build();

        // when
        entity.onCreate();

        // then
        assertThat(entity.getCreatedAt()).isEqualTo(fixed);
    }
}
