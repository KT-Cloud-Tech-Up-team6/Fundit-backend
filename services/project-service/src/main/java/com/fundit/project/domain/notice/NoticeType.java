package com.fundit.project.domain.notice;

import java.util.Arrays;

/**
 * 새소식 유형. 값 자체가 화면에 그대로 노출되는 한글 라벨이라 DB·API 모두 라벨 문자열로 주고받고,
 * 여기서는 허용 목록 검증만 담당한다.
 */
public enum NoticeType {

    REWARD_GUIDE("리워드안내"),
    EVENT("이벤트"),
    PRODUCTION("제작과정"),
    SHIPPING("발송정보"),
    ACHIEVEMENT("달성률"),
    EXCHANGE_REFUND("교환환불"),
    PAYMENT("결제안내"),
    FAQ("FAQ");

    private final String label;

    NoticeType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static boolean isSupported(String label) {
        return Arrays.stream(values()).anyMatch(type -> type.label.equals(label));
    }
}
