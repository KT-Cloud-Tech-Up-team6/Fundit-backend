package com.fundit.order.application.order;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.catalog.RewardCatalogClient;
import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import com.fundit.order.domain.coupon.CouponRepository;
import com.fundit.order.domain.coupon.DiscountType;
import com.fundit.order.domain.coupon.IssuerType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * ORDER-002(결제금액 계산)/ORDER-010(쿠폰 적용)의 공통 계산 로직. OrderPreviewService(비영속)와
 * OrderCreateService(영속) 둘 다 이 서비스로 금액을 계산해 서버 재계산 원칙(S4)을 한 곳에서 지킨다.
 * 부작용(재고 차감, Funding 저장)은 절대 하지 않는다 — 순수 계산 전용.
 */
@Service
public class OrderPricingService {

    private final RewardCatalogClient rewardCatalogClient;
    private final CouponRepository couponRepository;
    private final CouponIssuanceRepository couponIssuanceRepository;
    private final long defaultShippingFee;

    public OrderPricingService(RewardCatalogClient rewardCatalogClient,
                                CouponRepository couponRepository,
                                CouponIssuanceRepository couponIssuanceRepository,
                                @Value("${order.policy.default-shipping-fee}") long defaultShippingFee) {
        this.rewardCatalogClient = rewardCatalogClient;
        this.couponRepository = couponRepository;
        this.couponIssuanceRepository = couponIssuanceRepository;
        this.defaultShippingFee = defaultShippingFee;
    }

    public PricingResult calculate(UUID memberId, Long projectId, List<OrderLineItemRequest> lineItemRequests,
                                    List<String> couponCodes) {
        validateCouponCodeCount(couponCodes);

        List<RewardCatalogClient.RewardSnapshot> rewards = rewardCatalogClient.getRewards(projectId);
        List<ResolvedLineItem> resolvedLineItems = new ArrayList<>();
        long rewardAmount = 0;
        for (OrderLineItemRequest request : lineItemRequests) {
            RewardCatalogClient.RewardSnapshot snapshot = rewards.stream()
                    .filter(r -> r.rewardId().equals(request.rewardId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND,
                            "존재하지 않는 리워드입니다: " + request.rewardId()));
            List<ResolvedOption> options = resolveOptions(snapshot, request.optionValueIds());
            resolvedLineItems.add(new ResolvedLineItem(snapshot.rewardId(), snapshot.name(),
                    request.quantity(), snapshot.price(), options));
            rewardAmount += snapshot.price() * request.quantity();
        }

        long shippingFee = defaultShippingFee;
        CouponResolution couponResolution = resolveCoupons(memberId, projectId, couponCodes, rewardAmount, shippingFee);
        long finalAmount = rewardAmount + shippingFee - couponResolution.totalDiscount();

        return new PricingResult(rewardAmount, shippingFee, couponResolution.totalDiscount(), finalAmount,
                resolvedLineItems, couponResolution.applied(), couponResolution.unavailable());
    }

    private List<ResolvedOption> resolveOptions(RewardCatalogClient.RewardSnapshot snapshot, List<Long> optionValueIds) {
        if (optionValueIds == null || optionValueIds.isEmpty()) {
            return List.of();
        }
        List<ResolvedOption> options = new ArrayList<>();
        for (Long optionValueId : optionValueIds) {
            RewardCatalogClient.ResolvedOption resolved = snapshot.resolveOption(optionValueId)
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT,
                            "존재하지 않는 옵션입니다: " + optionValueId));
            options.add(new ResolvedOption(resolved.groupId(), resolved.groupName(), resolved.valueId(), resolved.value()));
        }
        return options;
    }

    private void validateCouponCodeCount(List<String> couponCodes) {
        if (couponCodes != null && couponCodes.size() > 2) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "쿠폰은 최대 2개까지 적용할 수 있습니다.");
        }
    }

    private CouponResolution resolveCoupons(UUID memberId, Long projectId, List<String> couponCodes,
                                             long rewardAmount, long shippingFee) {
        if (couponCodes == null || couponCodes.isEmpty()) {
            return new CouponResolution(List.of(), List.of(), 0);
        }

        List<Coupon> coupons = couponCodes.stream()
                .map(code -> couponRepository.findByCouponCode(code).orElse(null))
                .toList();

        // 같은 issuer_type 쿠폰 2개 이상 지정 — 순수 입력 형식 오류(400), 플랫폼쿠폰 1개+메이커쿠폰 1개까지만 허용.
        Set<IssuerType> issuerTypes = new HashSet<>();
        for (Coupon coupon : coupons) {
            if (coupon != null && !issuerTypes.add(coupon.getIssuerType())) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT, "같은 발급주체의 쿠폰은 함께 적용할 수 없습니다.");
            }
        }

        List<AppliedCoupon> applied = new ArrayList<>();
        List<UnavailableCoupon> unavailable = new ArrayList<>();
        for (int i = 0; i < couponCodes.size(); i++) {
            String code = couponCodes.get(i);
            Coupon coupon = coupons.get(i);
            resolveSingleCoupon(memberId, projectId, code, coupon, rewardAmount)
                    .ifPresentOrElse(
                            issuance -> {
                                long discount = coupon.calculateDiscount(rewardAmount, shippingFee);
                                applied.add(new AppliedCoupon(issuance.getId(), code, coupon.getIssuerType(),
                                        coupon.getDiscountType(), discount));
                            },
                            () -> unavailable.add(new UnavailableCoupon(code, unavailableReason(memberId, projectId, code, coupon, rewardAmount))));
        }
        long totalDiscount = applied.stream().mapToLong(AppliedCoupon::discountAmount).sum();
        return new CouponResolution(applied, unavailable, totalDiscount);
    }

    private Optional<CouponIssuance> resolveSingleCoupon(UUID memberId, Long projectId, String code, Coupon coupon,
                                                           long rewardAmount) {
        if (coupon == null) {
            return Optional.empty();
        }
        Optional<CouponIssuance> issuanceOpt = couponIssuanceRepository.findByCouponCodeAndOwnerId(code, memberId);
        if (issuanceOpt.isEmpty() || !issuanceOpt.get().isAvailable()) {
            return Optional.empty();
        }
        if (coupon.isExpired(Instant.now())) {
            return Optional.empty();
        }
        if (!coupon.meetsMinFundingAmount(rewardAmount)) {
            return Optional.empty();
        }
        if (!coupon.matchesProject(projectId)) {
            return Optional.empty();
        }
        return issuanceOpt;
    }

    private String unavailableReason(UUID memberId, Long projectId, String code, Coupon coupon, long rewardAmount) {
        if (coupon == null) {
            return "NOT_FOUND";
        }
        Optional<CouponIssuance> issuanceOpt = couponIssuanceRepository.findByCouponCodeAndOwnerId(code, memberId);
        if (issuanceOpt.isEmpty()) {
            return "NOT_OWNED";
        }
        if (!issuanceOpt.get().isAvailable()) {
            return "ALREADY_USED";
        }
        if (coupon.isExpired(Instant.now())) {
            return "EXPIRED";
        }
        if (!coupon.meetsMinFundingAmount(rewardAmount)) {
            return "MIN_AMOUNT_NOT_MET";
        }
        if (!coupon.matchesProject(projectId)) {
            return "NOT_APPLICABLE";
        }
        return "NOT_APPLICABLE";
    }

    public record PricingResult(long rewardAmount, long shippingFee, long discountAmount, long finalAmount,
                                 List<ResolvedLineItem> lineItems, List<AppliedCoupon> appliedCoupons,
                                 List<UnavailableCoupon> unavailableCoupons) {
    }

    public record ResolvedLineItem(Long rewardId, String rewardName, int quantity, long unitPrice,
                                    List<ResolvedOption> options) {
    }

    public record ResolvedOption(Long groupId, String groupName, Long valueId, String value) {
    }

    public record AppliedCoupon(Long couponIssuanceId, String couponCode, IssuerType issuerType,
                                 DiscountType discountType, long discountAmount) {
    }

    public record UnavailableCoupon(String couponCode, String reason) {
    }

    private record CouponResolution(List<AppliedCoupon> applied, List<UnavailableCoupon> unavailable, long totalDiscount) {
    }
}
