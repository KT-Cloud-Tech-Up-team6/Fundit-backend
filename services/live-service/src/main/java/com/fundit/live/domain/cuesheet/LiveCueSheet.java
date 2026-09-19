package com.fundit.live.domain.cuesheet;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.domain.ai.GenerationStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * AI 큐시트(요구사항정의서 6.2.4.2). <b>세션당 1개</b>라 sessionId가 곧 식별자다.
 *
 * <p>JPA 엔티티가 아니라 여기에 두는 이유는 {@code GENERATING → COMPLETED/FAILED} 전이 때문이다
 * (persistence-convention.md 0번 — 상태 전이 규칙이 있으면 복잡 애그리거트).
 * 전이를 엔티티에 두면 "생성 중에는 수정 불가", "끝난 걸 다시 완료 처리 불가" 같은 규칙이
 * 호출부마다 흩어진다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LiveCueSheet {

    /**
     * 구간 JSON 직렬화 길이 상한. 외부(AI·판매자)가 밀어넣는 값이라 상한이 없으면
     * JSONB 컬럼에 그대로 들어간다 — 10분짜리 방송 큐시트에 64KB면 넉넉하다.
     *
     * <p>구간 <b>요소 스키마</b>는 검증하지 않는다. 그건 AI 계약이고 아직 확정 전이라,
     * 지금 타입으로 박으면 AI가 필드 하나 늘릴 때마다 우리가 배포해야 한다.
     * JSON 문법 유효성은 경계(Jackson)가 본다.
     */
    private static final int MAX_SEGMENTS_LENGTH = 64 * 1024;

    private final Long sessionId;
    private final String mode;
    private GenerationStatus status;
    private final int targetDurationSec;
    private String segments;
    private String failureReason;

    /** 생성 요청. 재생성도 같은 경로이고 기존 큐시트를 덮어쓴다 — 이력 보관 요구가 없다. */
    public static LiveCueSheet requestGeneration(Long sessionId, String mode, int targetDurationSec) {
        return LiveCueSheet.builder()
                .sessionId(sessionId)
                .mode(mode)
                .status(GenerationStatus.GENERATING)
                .targetDurationSec(targetDurationSec)
                .build();
    }

    /** AI 생성 성공. 생성 중이 아니면 이미 결론이 난 큐시트라 덮어쓰지 않는다. */
    public void complete(String segments) {
        requireGenerating();
        requireValidSegments(segments);
        this.status = GenerationStatus.COMPLETED;
        this.segments = segments;
        this.failureReason = null;
    }

    public void fail(String reason) {
        requireGenerating();
        this.status = GenerationStatus.FAILED;
        this.failureReason = reason;
    }

    /** 판매자 직접 수정 — 구간 추가·순서 변경도 이 경로다. 생성 중에는 결과가 덮어쓴다. */
    public void replaceSegments(String segments) {
        if (isGenerating()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "생성이 끝난 뒤에 수정할 수 있습니다.");
        }
        requireValidSegments(segments);
        this.segments = segments;
    }

    public boolean isGenerating() {
        return status == GenerationStatus.GENERATING;
    }

    /** AI가 밀어주는 경로와 판매자가 고치는 경로가 같은 검사를 지난다. */
    private static void requireValidSegments(String segments) {
        if (segments == null || segments.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "구간이 비어 있습니다.");
        }
        if (segments.length() > MAX_SEGMENTS_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "구간이 너무 깁니다.");
        }
    }

    private void requireGenerating() {
        if (!isGenerating()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "이미 생성이 끝난 큐시트입니다.");
        }
    }
}
