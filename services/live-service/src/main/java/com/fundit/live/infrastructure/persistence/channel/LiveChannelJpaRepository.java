package com.fundit.live.infrastructure.persistence.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LiveChannelJpaRepository extends JpaRepository<LiveChannelJpaEntity, Long> {

    Optional<LiveChannelJpaEntity> findBySellerId(UUID sellerId);

    /** IVS 녹화 완료 이벤트는 채널 ARN만 알려준다(#222). */
    Optional<LiveChannelJpaEntity> findByIvsChannelArn(String ivsChannelArn);
}
