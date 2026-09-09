package com.fundit.payment.application.payment;

import java.security.SecureRandom;

/**
 * 토스 규격(영문 대소문자/숫자/-/_, 6~64자)에 맞는 {@code pg_order_id}를 채번한다.
 * 내부 PK(UUID)를 그대로 노출하지 않기 위해 별도 랜덤값을 쓴다(PAYMENT-001 처리 내용 ③).
 */
public final class PgOrderIdGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int RANDOM_PART_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PgOrderIdGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder("fundit-");
        for (int i = 0; i < RANDOM_PART_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
