package com.fundit.live.presentation.dto;

import java.util.List;

/**
 * {@code referenceChunks}는 근거가 아니라 판매자 참고용이다 — AI가 확인 못 한 사실은
 * {@code draftAnswer}에 {@code [판매자 확인 필요: ...]}로 비워둔다(임의 생성 금지).
 */
public record AiAnswerResponse(String draftAnswer, List<String> referenceChunks, boolean sent) {
}
