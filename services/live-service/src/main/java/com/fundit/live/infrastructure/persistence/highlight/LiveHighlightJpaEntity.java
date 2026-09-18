package com.fundit.live.infrastructure.persistence.highlight;

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
 * 타임라인 마커와 쇼츠 클립을 {@code kind}로 구분해 <b>한 테이블</b>에 담는다 —
 * 컬럼이 거의 같아 쪼개면 수정·재생성·공개설정 API가 전부 두 벌이 된다. 응답만 두 배열로 나눈다.
 */
@Getter
@Entity
@Builder
@Table(name = "live_highlights")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveHighlightJpaEntity {

    public static final String KIND_MARKER = "MARKER";
    public static final String KIND_CLIP = "CLIP";
    public static final String STATUS_GENERATING = "GENERATING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "kind", nullable = false, updatable = false)
    private String kind;

    @Column(name = "scene_label", nullable = false)
    private String sceneLabel;

    @Column(name = "title")
    private String title;

    @Column(name = "start_sec", nullable = false)
    private int startSec;

    /** MARKER는 시점이라 null이다. */
    @Column(name = "end_sec")
    private Integer endSec;

    @Column(name = "clip_url")
    private String clipUrl;

    @Column(name = "caption")
    private String caption;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "generation_status", nullable = false)
    private String generationStatus;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "click_count", nullable = false)
    private int clickCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** 판매자 검토·수정(요구사항정의서 6.6.4). null은 건드리지 않는다. */
    public void edit(Integer startSec, Integer endSec, String sceneLabel, String title, String caption) {
        if (startSec != null) this.startSec = startSec;
        if (endSec != null) this.endSec = endSec;
        if (sceneLabel != null) this.sceneLabel = sceneLabel;
        if (title != null) this.title = title;
        if (caption != null) this.caption = caption;
    }

    /** 생성 실패한 항목은 공개할 수 없다 — 재생 불가한 클립이 소비자 화면에 올라간다. */
    public boolean isPublishable() {
        return STATUS_COMPLETED.equals(this.generationStatus);
    }

    public void changeVisibility(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public void markRegenerating() {
        this.generationStatus = STATUS_GENERATING;
        // 재생성 중인 항목이 공개된 채로 남으면 소비자가 옛 클립을 본다.
        this.isPublic = false;
    }
}
