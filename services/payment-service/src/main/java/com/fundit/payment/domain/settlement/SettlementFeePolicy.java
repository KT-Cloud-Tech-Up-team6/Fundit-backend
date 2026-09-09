package com.fundit.payment.domain.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 순수 계산 규칙 — 플랫폼 수수료율 정책(PRD 9장 기준 3%, PAYMENT-013/014 처리 내용). */
public final class SettlementFeePolicy {

    public static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.03");

    private SettlementFeePolicy() {
    }

    public static long calculatePlatformFee(long grossAmount) {
        return BigDecimal.valueOf(grossAmount)
                .multiply(PLATFORM_FEE_RATE)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
