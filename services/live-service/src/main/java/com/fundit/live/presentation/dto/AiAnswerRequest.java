package com.fundit.live.presentation.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * {@code action}이 GENERATE면 초안만 만들고, SEND여야 실제 채팅에 게시된다 —
 * 자동 게시가 아니다(협의 확정).
 */
public record AiAnswerRequest(@NotBlank String action, String finalAnswer, List<String> productContext) {

    public boolean isSend() {
        return "SEND".equals(action);
    }

    public List<String> contextOrEmpty() {
        return productContext == null ? List.of() : productContext;
    }
}
