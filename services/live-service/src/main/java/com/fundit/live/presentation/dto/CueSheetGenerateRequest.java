package com.fundit.live.presentation.dto;

import com.fundit.live.application.ai.AiClient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code mode}는 SCENARIO(진행 순서 + 구간별 주요 내용 + 예상 시간 + 핵심 포인트) 또는
 * SCRIPT(위 전부 + 구간별 완성 대사)다(요구사항정의서 6.2.4.2).
 *
 * <p>{@code productDescription}~{@code deliverySchedule}은 판매자가 생성 전에 자유서술로 답한 5가지다(AI-1).
 * 전부 선택이고 저장하지 않고 AI로 전달만 한다.
 */
public record CueSheetGenerateRequest(
        @NotNull Mode mode,
        @Positive int targetDurationSec,
        // Boolean(박싱)인 이유: 선택 필드인데 primitive면 Jackson 3이 값 누락을 400으로 떨군다.
        // FE가 쓰지 않는 필드까지 전부 채워 보내야 하는 API가 된다.
        Boolean demoAvailable,
        List<String> emphasisPoints,
        String tone,
        List<String> mandatoryPhrases,
        @Size(max = 1000) String productDescription,
        @Size(max = 1000) String motivation,
        @Size(max = 1000) String expectedRisks,
        @Size(max = 1000) String demoDescription,
        @Size(max = 1000) String deliverySchedule
) {
    /** 값 검증을 DB 제약까지 미루면 400이 아니라 500이 난다. */
    public enum Mode {SCENARIO, SCRIPT}

    public boolean demoAvailableOrFalse() {
        return Boolean.TRUE.equals(demoAvailable);
    }

    public List<String> emphasisOrEmpty() {
        return emphasisPoints == null ? List.of() : emphasisPoints;
    }

    public List<String> mandatoryOrEmpty() {
        return mandatoryPhrases == null ? List.of() : mandatoryPhrases;
    }

    public AiClient.SellerBrief sellerBrief() {
        return new AiClient.SellerBrief(productDescription, motivation, expectedRisks, demoDescription, deliverySchedule);
    }
}
