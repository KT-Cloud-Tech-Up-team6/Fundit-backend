package com.fundit.payment.domain.refund;

/**
 * 원 결제수단 환불이 불가할 때(카드 만료/해지 등) 참여자가 입력하는 대체 계좌 정보.
 * 금융정보이므로 저장 시 애플리케이션 레벨로 암호화한다(security.md S9,
 * infrastructure의 {@code AlternateRefundAccountConverter} 참고 — 도메인 자체는 평문 값 객체).
 */
public record AlternateRefundAccount(String bankName, String accountHolder, String accountNumber) {
}
