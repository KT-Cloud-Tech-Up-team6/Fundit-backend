package com.fundit.payment.application.funding;

import java.util.UUID;

/**
 * order-service 내부 API({@code GET /internal/fundings/{fundingId}}) 아웃바운드 포트.
 *
 * <p>payment-service CLAUDE.md "가장 시급한 미해결 의존성" 기준 — order-service에 이
 * 엔드포인트가 아직 없다(별도 연동 이슈에서 배선). 지금은 {@code StubOrderFundingClient}로
 * 개발·테스트를 진행하고, 실제 HTTP 구현체({@code HttpOrderFundingClient})는 연동 이슈에서
 * 활성화한다(스프링 프로필/설정으로 전환).
 *
 * <p>[CLAUDE.md 원안 대비 확장] {@code sellerId}를 원안 {@code FundingSnapshot} 레코드에
 * 추가했다 — PAYMENT-007(하자환불 검토)이 "해당 주문의 판매자 본인만" 처리 가능해야 하는데
 * (security.md S4), 판매자 식별자를 얻을 다른 경로가 없다. order-service 내부 API가 실제로
 * 만들어질 때 이 필드도 함께 내려주도록 맞춰야 한다[정책 확인 필요].
 */
public interface OrderFundingClient {

    /**
     * @throws com.fundit.common.error.DependencyFailureException 호출 실패(타임아웃·5xx 포함) 시
     */
    FundingSnapshot fetch(Long fundingId);

    record FundingSnapshot(
            UUID memberId,
            UUID sellerId,
            String status,
            long finalAmount,
            String orderName,
            Long couponIssuanceId) {

        public boolean isPending() {
            return "PENDING".equals(status);
        }
    }
}
