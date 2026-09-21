package com.fundit.live.presentation.dto;

import jakarta.validation.constraints.NotNull;

/**
 * {@code action}이 GENERATE면 AI 초안 미리보기만 반환한다 — 아무것도 기록하지 않는다.
 * SEND여야 AI에 등록되고 소비자 Q&A 버튼에 노출된다. 실제 채팅 게시는 판매자 화면이
 * 자기 채팅 토큰으로 직접 한다({@code AiAnswerService} "[천장]" 참고).
 *
 * <p>이 흐름은 AI가 근거를 못 찾은(UNANSWERABLE) 질문 전용이다 — 근거를 찾은 질문은
 * 채팅 배치 응답으로 이미 즉시 답변되어 있다.
 */
public record AiAnswerRequest(@NotNull Action action, String finalAnswer) {

    /** 문자열로 두면 오타가 조용히 GENERATE로 흘러 판매자가 보낸 줄 아는 답변이 등록되지 않는다. */
    public enum Action {GENERATE, SEND}

    public boolean isSend() {
        return action == Action.SEND;
    }
}
