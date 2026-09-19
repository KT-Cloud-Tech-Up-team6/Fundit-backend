package com.fundit.live.presentation.dto;

/**
 * {@code grounded=false}는 상품정보에서 근거를 찾지 못했다는 뜻이다. 에러가 아니라 상태다 —
 * 판매자가 그대로 보내지 않도록 화면에서 경고한다.
 */
public record AiAnswerResponse(String draftAnswer, boolean grounded, boolean sent) {
}
