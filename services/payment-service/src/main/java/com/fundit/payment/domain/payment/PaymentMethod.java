package com.fundit.payment.domain.payment;

/** 토스 Payment.method 대분류. 간편결제 브랜드는 {@link Payment#getEasyPayProvider()}에 별도 저장. */
public enum PaymentMethod {
    CARD,
    VIRTUAL_ACCOUNT,
    TRANSFER,
    EASY_PAY;

    /** 토스 응답의 한글 method 값("카드", "가상계좌", "계좌이체", "간편결제")을 매핑한다. */
    public static PaymentMethod fromTossMethod(String tossMethod) {
        if (tossMethod == null) {
            return null;
        }
        return switch (tossMethod) {
            case "카드" -> CARD;
            case "가상계좌" -> VIRTUAL_ACCOUNT;
            case "계좌이체" -> TRANSFER;
            case "간편결제" -> EASY_PAY;
            default -> throw new IllegalArgumentException("알 수 없는 토스 결제수단: " + tossMethod);
        };
    }
}
