package com.fundit.live.infrastructure.persistence.session;

import com.fundit.live.domain.session.LiveStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** DB 컬럼 매핑 전용. 도메인 규칙은 {@link com.fundit.live.domain.session.LiveSession}에 있다. */
@Getter
@Entity
@Builder
@Table(name = "live_sessions")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveSessionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "channel_id", nullable = false, updatable = false)
    private Long channelId;

    @Column(name = "category_major")
    private String categoryMajor;

    @Column(name = "category_minor")
    private String categoryMinor;

    @Column(name = "intro_text")
    private String introText;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    // 열거값을 DB CHECK로 두고 앱은 문자열로 저장한다 — 레포가 ORDINAL을 쓰지 않는 관행을 따른다.
    // ORDINAL이면 enum 상수 순서를 바꾸는 순간 기존 행의 의미가 조용히 달라진다.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LiveStatus status;

    @Column(name = "scheduled_start_at")
    private Instant scheduledStartAt;

    @Column(name = "actual_start_at")
    private Instant actualStartAt;

    @Column(name = "actual_end_at")
    private Instant actualEndAt;

    @Column(name = "vod_url")
    private String vodUrl;

    @Column(name = "vod_ready_at")
    private Instant vodReadyAt;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "ivs_chat_room_arn")
    private String ivsChatRoomArn;

    @Column(name = "error_detail")
    private String errorDetail;

    @Column(name = "error_occurred_at")
    private Instant errorOccurredAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
