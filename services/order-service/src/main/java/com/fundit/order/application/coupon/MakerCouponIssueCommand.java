package com.fundit.order.application.coupon;

import com.fundit.order.domain.coupon.DiscountType;
import com.fundit.order.domain.coupon.DropType;
import com.fundit.order.domain.coupon.IssueChannel;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code liveSessionId}/{@code liveSellerId}는 {@code issueChannel=LIVE}일 때만 채워진다 —
 * 컨트롤러가 트랜잭션 밖에서 liveId(UUID)를 조회해 내부 세션 PK와 방송 소유자를 해석한 결과다
 * (판매자 FE도 BIGINT 세션 PK를 모른다). GENERAL이면 둘 다 null이고 live 호출 자체가 없다.
 */
public record MakerCouponIssueCommand(
        UUID projectId,
        String couponName,
        DiscountType discountType,
        long discountValue,
        Long maxDiscountAmount,
        Long budgetLimit,
        int quantity,
        long minFundingAmount,
        int perMemberLimit,
        Instant expiresAt,
        IssueChannel issueChannel,
        DropType dropType,
        Long liveSessionId,
        UUID liveSellerId
) {
}
