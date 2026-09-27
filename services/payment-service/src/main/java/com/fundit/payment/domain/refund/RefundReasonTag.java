package com.fundit.payment.domain.refund;

/**
 * 발송 후 신청 3종(하자환불·반품·교환)의 사유 유형을 {@code refund_requests.reason_detail} 앞에
 * {@code "[DAMAGED] 파손"} 형태로 붙여 저장하기 위한 포맷. 사유 유형 컬럼이 없어(PaymentERD.md
 * 3장) 문자열에 합쳐 두는데, 붙이는 쪽(요청 DTO)과 떼는 쪽(조회 응답)이 흩어지면 포맷이
 * 어긋나므로 한곳에 모았다. 조회 응답은 {@link #parse}로 다시 나눠 내려보낸다 — FE가 이
 * 문자열을 파싱하지 않게 하기 위함이다.
 *
 * <p>ponytail: 사유별 집계·필터가 필요해지면 {@code reason_type} 컬럼(+ 기존 행 태그 백필)으로
 * 승격한다. 지금은 조회 시 분리만 하면 되어 마이그레이션을 만들지 않는다.
 */
public final class RefundReasonTag {

    private RefundReasonTag() {
    }

    /** {@code "[" + 유형 + "] " + 상세} — 상세가 없으면 태그만 남는다(기존 저장 포맷 그대로다). */
    public static String format(Enum<?> reasonType, String detail) {
        return "[" + reasonType.name() + "] " + (detail == null ? "" : detail);
    }

    /**
     * 태그가 붙은 저장값을 (유형, 상세)로 나눈다. 태그가 없는 값(발송지연·목표미달 등 유형 없는
     * 사유, 태그 도입 이전 데이터)은 유형 null에 원문 그대로다.
     */
    public static Parsed parse(String reasonDetail) {
        if (reasonDetail == null) {
            return new Parsed(null, null);
        }
        int close = reasonDetail.indexOf(']');
        if (!reasonDetail.startsWith("[") || close < 0) {
            return new Parsed(null, emptyToNull(reasonDetail));
        }
        String reasonType = reasonDetail.substring(1, close);
        // 구매자가 직접 쓴 상세가 "["로 시작하는 경우를 유형으로 오인하지 않도록 enum 이름 모양만 인정한다.
        if (!reasonType.matches("[A-Z][A-Z_]*")) {
            return new Parsed(null, emptyToNull(reasonDetail));
        }
        return new Parsed(reasonType, emptyToNull(reasonDetail.substring(close + 1).strip()));
    }

    private static String emptyToNull(String value) {
        return value.isBlank() ? null : value;
    }

    /** {@code reasonType}은 유형 없는 사유에서, {@code detail}은 상세를 입력하지 않은 신청에서 null이다. */
    public record Parsed(String reasonType, String detail) {
    }
}
