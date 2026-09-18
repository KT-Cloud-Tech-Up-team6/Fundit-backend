package com.fundit.live.infrastructure.persistence.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LiveChannelJpaRepository extends JpaRepository<LiveChannelJpaEntity, Long> {

    Optional<LiveChannelJpaEntity> findBySellerId(UUID sellerId);
}
