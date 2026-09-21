package com.fundit.payment.domain.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/** 순수 계산 규칙 — 플랫폼 수수료율/선정산 지급률 정책(PRD 9장 기준, PAYMENT-013/014 처리 내용). */
public final class SettlementFeePolicy {

    public static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.03");
    /** PRD 9.1.3 — 선정산은 정산 예정 금액의 70%를 일괄 우선 지급한다. */
    public static final BigDecimal INTERIM_PAYOUT_RATE = new BigDecimal("0.7");
    /**
     * PRD 9.1.3 — 최종정산은 마지막 배송완료일로부터 14일 후 정산 내역서를 발송한다.
     * {@code SettlementScheduleService}(배치 생성 스케줄)와 {@code SettlementDisputeService}
     * (이의신청 기산일 계산)가 이 값을 공유한다 — 두 곳에서 "14일"을 따로 들고 있으면
     * 하나만 바뀌었을 때 조용히 어긋난다.
     */
    public static final Duration FINAL_SETTLEMENT_NOTICE_DELAY = Duration.ofDays(14);

    private SettlementFeePolicy() {
    }

    public static long calculatePlatformFee(long grossAmount) {
        return BigDecimal.valueOf(grossAmount)
                .multiply(PLATFORM_FEE_RATE)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static long calculateInterimPayout(long netAmount) {
        return BigDecimal.valueOf(netAmount)
                .multiply(INTERIM_PAYOUT_RATE)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
