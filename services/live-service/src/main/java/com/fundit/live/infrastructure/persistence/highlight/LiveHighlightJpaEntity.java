package com.fundit.live.infrastructure.persistence.highlight;

import com.fundit.live.domain.ai.GenerationStatus;
import com.fundit.live.domain.highlight.HighlightKind;
import com.fundit.live.domain.highlight.LiveHighlight;
import com.fundit.live.domain.highlight.SceneLabel;
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

/**
 * 타임라인 마커와 쇼츠 클립을 {@code kind}로 구분해 <b>한 테이블</b>에 담는다 —
 * 컬럼이 거의 같아 쪼개면 수정·재생성·공개설정 API가 전부 두 벌이 된다. 응답만 두 배열로 나눈다.
 *
 * <p>저장 매핑만 한다. 구간 불변식·상태 전이·공개 가능 여부는
 * {@link LiveHighlight}에 있다(persistence-convention.md 1번).
 */
@Getter
@Entity
@Builder
@Table(name = "live_highlights")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveHighlightJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "kind", nullable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    private HighlightKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "scene_label", nullable = false)
    private SceneLabel sceneLabel;

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
    @Enumerated(EnumType.STRING)
    private GenerationStatus generationStatus;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "click_count", nullable = false)
    private int clickCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /**
     * 관리 엔티티에 도메인 변경분을 옮긴다. 조회·클릭 수는 <b>건드리지 않는다</b> —
     * 조건부 UPDATE로 올리는 값이라 읽은 시점의 값으로 되돌리면 집계가 사라진다.
     */
    void applyFrom(LiveHighlight highlight) {
        this.sceneLabel = highlight.getSceneLabel();
        this.title = highlight.getTitle();
        this.startSec = highlight.getStartSec();
        this.endSec = highlight.getEndSec();
        this.clipUrl = highlight.getClipUrl();
        this.caption = highlight.getCaption();
        this.isPublic = highlight.isPublic();
        this.generationStatus = highlight.getGenerationStatus();
    }
}
