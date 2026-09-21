package com.fundit.live.presentation.dto;

import com.fundit.live.application.ai.AiClient;

/**
 * 대표질문 원본 채팅(요구사항정의서 6.4.4.3). AI의 {@code GET /faq/{qid}/comments} 응답을
 * 그대로 반영한다 — 원본은 이제 AI가 갖고 있고 우리는 로컬에 사본을 두지 않는다.
 */
public record OriginalMessageResponse(String commentId, String content, long atMs) {

    public static OriginalMessageResponse from(AiClient.FaqComment c) {
        return new OriginalMessageResponse(c.commentId(), c.text(), c.atMs());
    }
}
