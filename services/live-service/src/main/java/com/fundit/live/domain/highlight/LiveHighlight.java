package com.fundit.live.domain.highlight;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.domain.ai.GenerationStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * LIVE 하이라이트(요구사항정의서 6.6.4).
 *
 * <p>JPA 엔티티가 아니라 여기에 두는 이유는 구간 불변식({@code endSec > startSec})과
 * 상태 전이({@code GENERATING → COMPLETED/FAILED}), 그리고 "실패분은 공개 불가"가 있기 때문이다
 * (persistence-convention.md 0번). 이전에는 JPA 엔티티가 {@code BusinessException}을 던지고
 * 공개 가능 여부는 서비스가 {@code if}로 따로 보고 있어 규칙이 두 군데로 갈려 있었다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LiveHighlight {

    private final Long id;
    private final UUID publicId;
    private final Long sessionId;
    private final HighlightKind kind;
    private SceneLabel sceneLabel;
    private String title;
    private int startSec;
    private Integer endSec;
    private String clipUrl;
    private String caption;
    private boolean isPublic;
    private GenerationStatus generationStatus;
    private final int viewCount;
    private final int clickCount;
    private final Instant createdAt;

    /** AI 생성 결과. <b>항상 비공개로 시작한다</b> — 기본값을 뒤집으면 검수 전 내용이 그대로 샌다. */
    public static LiveHighlight generated(Long sessionId, HighlightKind kind, SceneLabel sceneLabel,
                                          String title, int startSec, Integer endSec, String clipUrl,
                                          String caption, GenerationStatus status) {
        requireRange(kind, startSec, endSec);
        return LiveHighlight.builder()
                .publicId(UUID.randomUUID())
                .sessionId(sessionId)
                .kind(kind)
                .sceneLabel(sceneLabel)
                .title(title)
                .startSec(startSec)
                .endSec(endSec)
                .clipUrl(clipUrl)
                .caption(caption)
                .isPublic(false)
                .generationStatus(status)
                .viewCount(0)
                .clickCount(0)
                .build();
    }

    /** 판매자 검토·수정. null은 건드리지 않는다(부분 수정). */
    public void edit(Integer startSec, Integer endSec, SceneLabel sceneLabel, String title, String caption) {
        // 부분 수정이라 병합된 최종값으로 본다 — 바뀐 필드만 보면 MARKER에 endSec만
        // 따로 붙이는 요청을 못 잡는다.
        requireRange(this.kind, startSec != null ? startSec : this.startSec,
                endSec != null ? endSec : this.endSec);
        if (startSec != null) this.startSec = startSec;
        if (endSec != null) this.endSec = endSec;
        if (sceneLabel != null) this.sceneLabel = sceneLabel;
        if (title != null) this.title = title;
        if (caption != null) this.caption = caption;
    }

    /** 재생성 결과 반영. 검토 전 내용이 새지 않게 공개 여부를 다시 내린다. */
    public void applyRegenerated(SceneLabel sceneLabel, String title, int startSec, Integer endSec,
                                 String clipUrl, String caption, GenerationStatus status) {
        requireRange(this.kind, startSec, endSec);
        this.sceneLabel = sceneLabel;
        this.title = title;
        this.startSec = startSec;
        this.endSec = endSec;
        this.clipUrl = clipUrl;
        this.caption = caption;
        this.generationStatus = status;
        this.isPublic = false;
    }

    public void markRegenerating() {
        this.generationStatus = GenerationStatus.GENERATING;
        // 재생성 중인 항목이 공개된 채로 남으면 소비자가 옛 클립을 본다.
        this.isPublic = false;
    }

    /** 공개 설정. 생성에 실패한 항목은 재생 불가한 클립이라 소비자 화면에 올릴 수 없다. */
    public void changeVisibility(boolean isPublic) {
        if (isPublic && generationStatus != GenerationStatus.COMPLETED) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "생성에 실패한 항목은 공개할 수 없습니다.");
        }
        this.isPublic = isPublic;
    }

    public boolean isClip() {
        return kind == HighlightKind.CLIP;
    }

    /**
     * 뒤집힌 구간이 저장되면 클립 URL은 멀쩡한데 재생만 깨진다.
     *
     * <p>{@code endSec}은 <b>kind가 정한다</b> — CLIP은 구간이라 필수고, MARKER는 시점이라
     * 있으면 안 된다(DDL의 {@code end_sec}은 NULL 허용이라 DB가 막아주지 않는다).
     * 끝 없는 클립은 플레이어가 구간을 잡지 못한다.
     */
    private static void requireRange(HighlightKind kind, int startSec, Integer endSec) {
        if (startSec < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "시작 위치는 0 이상이어야 합니다.");
        }
        if (kind == HighlightKind.CLIP && endSec == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "클립은 종료 위치가 필요합니다.");
        }
        if (kind == HighlightKind.MARKER && endSec != null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "마커는 시점이라 종료 위치를 둘 수 없습니다.");
        }
        if (endSec != null && endSec <= startSec) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "종료 위치가 시작보다 뒤여야 합니다.");
        }
    }
}
