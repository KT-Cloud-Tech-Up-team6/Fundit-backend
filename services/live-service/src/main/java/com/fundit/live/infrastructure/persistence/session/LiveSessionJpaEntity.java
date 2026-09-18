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

    /**
     * 도메인이 소유한 필드만 반영한다.
     *
     * <p><b>{@code likeCount}·{@code vodUrl}·{@code vodReadyAt}은 건드리지 않는다.</b>
     * 이 값들은 다른 경로(좋아요 조건부 UPDATE, VOD 전환)가 쓰고 도메인은 읽기만 한다.
     * detached 엔티티를 만들어 merge하면 전 컬럼 UPDATE라 <b>읽은 시점의 stale 값이
     * 남의 갱신을 덮어쓴다</b> — 방송 종료 저장 한 번에 그 사이 들어온 좋아요가 증발한다.
     */
    void applyFrom(com.fundit.live.domain.session.LiveSession session) {
        this.categoryMajor = session.getCategoryMajor();
        this.categoryMinor = session.getCategoryMinor();
        this.introText = session.getIntroText();
        this.thumbnailUrl = session.getThumbnailUrl();
        this.status = session.getStatus();
        this.scheduledStartAt = session.getScheduledStartAt();
        this.actualStartAt = session.getActualStartAt();
        this.actualEndAt = session.getActualEndAt();
        this.ivsChatRoomArn = session.getIvsChatRoomArn();
        this.errorDetail = session.getErrorDetail();
        this.errorOccurredAt = session.getErrorOccurredAt();
    }
}
