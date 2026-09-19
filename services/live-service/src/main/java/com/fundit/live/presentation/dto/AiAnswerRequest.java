package com.fundit.live.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * {@code action}이 GENERATE면 초안만 만들고, SEND여야 실제 채팅에 게시된다 —
 * 자동 게시가 아니다(협의 확정).
 */
public record AiAnswerRequest(@NotNull Action action, String finalAnswer, List<String> productContext) {

    /** 문자열로 두면 오타가 조용히 GENERATE로 흘러 판매자가 보낸 줄 아는 답변이 게시되지 않는다. */
    public enum Action {GENERATE, SEND}

    public boolean isSend() {
        return action == Action.SEND;
    }

    public List<String> contextOrEmpty() {
        return productContext == null ? List.of() : productContext;
    }
}
