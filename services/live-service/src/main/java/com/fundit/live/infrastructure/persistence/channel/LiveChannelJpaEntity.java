package com.fundit.live.infrastructure.persistence.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * IVS 채널(영구 자원). 단순 애그리거트라 도메인 모델을 따로 두지 않는다
 * (persistence-convention.md 2번) — 값 저장·조회가 전부고 상태 전이가 없다.
 *
 * <p>{@code seller_id}가 UNIQUE라 판매자당 채널 1개다 = 동시에 두 방송을 송출할 수 없다.
 * 스트림 키는 평문으로 두지 않고 비밀관리 시스템의 참조 식별자만 담는다(S9).
 */
@Getter
@Entity
@Builder
@Table(name = "live_channels")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveChannelJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "ivs_channel_arn", nullable = false)
    private String ivsChannelArn;

    @Column(name = "ivs_ingest_endpoint", nullable = false)
    private String ivsIngestEndpoint;

    @Column(name = "ivs_playback_url", nullable = false)
    private String ivsPlaybackUrl;

    @Column(name = "ivs_stream_key_ref")
    private String ivsStreamKeyRef;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
