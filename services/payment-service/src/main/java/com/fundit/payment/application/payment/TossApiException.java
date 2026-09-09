package com.fundit.payment.application.payment;

import lombok.Getter;

/**
 * 토스 API가 명시적으로 실패 응답(4xx, {@code {code, message}} 바디)을 반환했을 때 던진다.
 * 네트워크 오류·타임아웃·5xx는 이게 아니라 {@link com.fundit.common.error.DependencyFailureException}으로
 * 감싼다 — "PG가 요청을 거절했다"와 "PG를 호출조차 못 했다"는 다른 상황이라 응답 코드 분기가 달라진다.
 */
@Getter
public class TossApiException extends RuntimeException {

    /** 세션(인증) 만료 — 토스 문서 기준 결제 승인 API가 반환하는 코드. */
    public static final String NOT_FOUND_PAYMENT_SESSION = "NOT_FOUND_PAYMENT_SESSION";

    private final String tossErrorCode;
    private final String tossMessage;

    public TossApiException(String tossErrorCode, String tossMessage) {
        super("토스 API 실패: " + tossErrorCode + " - " + tossMessage);
        this.tossErrorCode = tossErrorCode;
        this.tossMessage = tossMessage;
    }

    public boolean isSessionExpired() {
        return NOT_FOUND_PAYMENT_SESSION.equals(tossErrorCode);
    }
}
