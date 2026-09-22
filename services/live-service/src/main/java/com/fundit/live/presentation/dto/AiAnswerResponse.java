package com.fundit.live.presentation.dto;

import java.util.List;

/**
 * {@code referenceChunks}는 근거가 아니라 판매자 참고용이다. <b>이 배열이 비어 있는 것이
 * 곧 "근거 없음" 신호다</b> — 요구사항정의서 6.4.4.5의 Empty State는 별도 플래그가 아니라
 * 이 길이로 판단한다(AI의 {@code GET /unanswered/{qid}} 응답에 grounded 플래그가 없다).
 */
public record AiAnswerResponse(String draftAnswer, List<String> referenceChunks, boolean sent) {
}
