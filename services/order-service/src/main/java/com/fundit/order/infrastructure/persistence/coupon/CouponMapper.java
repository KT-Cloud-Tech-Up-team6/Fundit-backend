package com.fundit.order.infrastructure.persistence.coupon;

import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponTargetScope;
import com.fundit.order.domain.coupon.DiscountType;
import com.fundit.order.domain.coupon.DropType;
import com.fundit.order.domain.coupon.IssueChannel;
import com.fundit.order.domain.coupon.IssuerType;
import org.springframework.stereotype.Component;

@Component
public class CouponMapper {

    Coupon toDomain(CouponJpaEntity entity) {
        return Coupon.builder()
                .id(entity.getId())
                .couponCode(entity.getCouponCode())
                .couponName(entity.getCouponName())
                .discountType(DiscountType.valueOf(entity.getDiscountType()))
                .discountValue(entity.getDiscountValue())
                .maxDiscountAmount(entity.getMaxDiscountAmount())
                .budgetLimit(entity.getBudgetLimit())
                .usedBudgetAmount(entity.getUsedBudgetAmount())
                .issuerType(IssuerType.valueOf(entity.getIssuerType()))
                .issuerId(entity.getIssuerId())
                .targetScope(CouponTargetScope.valueOf(entity.getTargetScope()))
                .targetRefId(entity.getTargetRefId())
                .minFundingAmount(entity.getMinFundingAmount())
                .perMemberLimit(entity.getPerMemberLimit())
                .remainingQuantity(entity.getRemainingQuantity())
                .expiresAt(entity.getExpiresAt())
                .issueChannel(IssueChannel.valueOf(entity.getIssueChannel()))
                .liveSessionId(entity.getLiveSessionId())
                .dropType(entity.getDropType() == null ? null : DropType.valueOf(entity.getDropType()))
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    CouponJpaEntity toEntity(Coupon domain) {
        return CouponJpaEntity.builder()
                .id(domain.getId())
                .couponCode(domain.getCouponCode())
                .couponName(domain.getCouponName())
                .discountType(domain.getDiscountType().name())
                .discountValue(domain.getDiscountValue())
                .maxDiscountAmount(domain.getMaxDiscountAmount())
                .budgetLimit(domain.getBudgetLimit())
                .usedBudgetAmount(domain.getUsedBudgetAmount())
                .issuerType(domain.getIssuerType().name())
                .issuerId(domain.getIssuerId())
                .targetScope(domain.getTargetScope().name())
                .targetRefId(domain.getTargetRefId())
                .minFundingAmount(domain.getMinFundingAmount())
                .perMemberLimit(domain.getPerMemberLimit())
                .remainingQuantity(domain.getRemainingQuantity())
                .expiresAt(domain.getExpiresAt())
                .issueChannel(domain.getIssueChannel().name())
                .liveSessionId(domain.getLiveSessionId())
                .dropType(domain.getDropType() == null ? null : domain.getDropType().name())
                .version(domain.getVersion())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
