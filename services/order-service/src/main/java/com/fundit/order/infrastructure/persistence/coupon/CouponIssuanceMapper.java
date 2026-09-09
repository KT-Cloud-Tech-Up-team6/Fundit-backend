package com.fundit.order.infrastructure.persistence.coupon;

import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceStatus;
import org.springframework.stereotype.Component;

@Component
public class CouponIssuanceMapper {

    CouponIssuance toDomain(CouponIssuanceJpaEntity entity) {
        return CouponIssuance.builder()
                .id(entity.getId())
                .couponCode(entity.getCouponCode())
                .ownerId(entity.getOwnerId())
                .issuedAt(entity.getIssuedAt())
                .status(CouponIssuanceStatus.valueOf(entity.getStatus()))
                .usedFundingId(entity.getUsedFundingId())
                .usedAt(entity.getUsedAt())
                .restoredAt(entity.getRestoredAt())
                .build();
    }

    CouponIssuanceJpaEntity toEntity(CouponIssuance domain) {
        return CouponIssuanceJpaEntity.builder()
                .id(domain.getId())
                .couponCode(domain.getCouponCode())
                .ownerId(domain.getOwnerId())
                .issuedAt(domain.getIssuedAt())
                .status(domain.getStatus().name())
                .usedFundingId(domain.getUsedFundingId())
                .usedAt(domain.getUsedAt())
                .restoredAt(domain.getRestoredAt())
                .build();
    }
}
