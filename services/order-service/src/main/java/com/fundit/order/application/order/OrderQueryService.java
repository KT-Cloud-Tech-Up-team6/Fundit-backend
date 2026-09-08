package com.fundit.order.application.order;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaEntity;
import com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** ORDER-004(내 펀딩 참여 목록)/ORDER-005(개별 참여 상세) 조회 전용. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final FundingRepository fundingRepository;
    private final FundingCouponApplicationJpaRepository couponApplicationJpaRepository;

    public Page<Funding> listMyOrders(UUID memberId, FundingStatus status, Pageable pageable) {
        return fundingRepository.findByMemberId(memberId, status, pageable);
    }

    /** orderId(public_id) 소유권 서버 검증 — 타인 주문 접근 시 FORBIDDEN(S4). */
    public FundingDetail getDetail(UUID memberId, UUID orderId) {
        Funding funding = fundingRepository.findByPublicId(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!funding.isOwnedBy(memberId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        long discountAmount = couponApplicationJpaRepository.findByFundingId(funding.getId()).stream()
                .mapToLong(FundingCouponApplicationJpaEntity::getDiscountAmount)
                .sum();
        return new FundingDetail(funding, discountAmount);
    }

    public record FundingDetail(Funding funding, long discountAmount) {

        public long finalAmount() {
            return funding.totalRewardAmount() + funding.getShippingFee() - discountAmount;
        }
    }
}
