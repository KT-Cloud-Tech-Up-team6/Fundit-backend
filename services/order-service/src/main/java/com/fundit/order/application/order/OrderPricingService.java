package com.fundit.order.application.order;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.catalog.ProjectOwnershipClient;
import com.fundit.order.application.catalog.ProjectSummaryClient;
import com.fundit.order.application.catalog.RewardCatalogClient;
import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import com.fundit.order.domain.coupon.CouponIssuanceStatus;
import com.fundit.order.domain.coupon.CouponRepository;
import com.fundit.order.domain.coupon.CouponTargetScope;
import com.fundit.order.domain.coupon.DiscountType;
import com.fundit.order.domain.coupon.IssuerType;
import com.fundit.order.domain.coupon.ProjectMatchContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final ProjectSummaryClient projectSummaryClient;
    private final ProjectOwnershipClient projectOwnershipClient;
    private final long defaultShippingFee;

    public OrderPricingService(RewardCatalogClient rewardCatalogClient,
                                CouponRepository couponRepository,
                                CouponIssuanceRepository couponIssuanceRepository,
                                ProjectSummaryClient projectSummaryClient,
                                ProjectOwnershipClient projectOwnershipClient,
                                @Value("${order.policy.default-shipping-fee}") long defaultShippingFee) {
        this.rewardCatalogClient = rewardCatalogClient;
        this.couponRepository = couponRepository;
        this.couponIssuanceRepository = couponIssuanceRepository;
        this.projectSummaryClient = projectSummaryClient;
        this.projectOwnershipClient = projectOwnershipClient;
        this.defaultShippingFee = defaultShippingFee;
    }

    /**
     * @param autoApplyBestCoupon true면 {@code couponCodes}를 무시하고 회원이 보유한 쿠폰 중
     *                            발급주체(플랫폼/메이커)별로 할인액이 가장 큰 것을 자동 적용한다(ORDER-010 최적 추천).
     */
    public PricingResult calculate(UUID memberId, UUID projectId, List<OrderLineItemRequest> lineItemRequests,
                                    List<String> couponCodes, boolean autoApplyBestCoupon) {
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
        CouponResolution couponResolution = autoApplyBestCoupon
                ? autoResolveCoupons(memberId, projectId, rewardAmount, shippingFee)
                : resolveCoupons(memberId, projectId, couponCodes, rewardAmount, shippingFee);
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

    private CouponResolution resolveCoupons(UUID memberId, UUID projectId, List<String> couponCodes,
                                             long rewardAmount, long shippingFee) {
        if (couponCodes == null || couponCodes.isEmpty()) {
            return new CouponResolution(List.of(), List.of(), 0);
        }
        if (couponCodes.size() > 2) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "쿠폰은 최대 2개까지 적용할 수 있습니다.");
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

        ProjectMatchContext matchContext = buildMatchContext(projectId, coupons);

        List<AppliedCoupon> applied = new ArrayList<>();
        List<UnavailableCoupon> unavailable = new ArrayList<>();
        for (int i = 0; i < couponCodes.size(); i++) {
            String code = couponCodes.get(i);
            Coupon coupon = coupons.get(i);
            resolveSingleCoupon(memberId, projectId, coupon, rewardAmount, shippingFee, matchContext)
                    .ifPresentOrElse(
                            resolved -> applied.add(new AppliedCoupon(resolved.issuance().getId(), code,
                                    coupon.getIssuerType(), coupon.getDiscountType(), resolved.discount())),
                            () -> unavailable.add(new UnavailableCoupon(code,
                                    unavailableReason(memberId, projectId, code, coupon, rewardAmount, shippingFee, matchContext))));
        }
        long totalDiscount = applied.stream().mapToLong(AppliedCoupon::discountAmount).sum();
        return new CouponResolution(applied, unavailable, totalDiscount);
    }

    /**
     * ORDER-010 최적 쿠폰 추천 — 회원이 보유한(AVAILABLE) 쿠폰 전부를 후보로 놓고, 적용 가능한
     * 것 중 발급주체(issuer_type)별로 할인액이 가장 큰 것 하나씩만(최대 플랫폼 1 + 메이커 1) 채택한다.
     * 후보에서 탈락한 쿠폰은 사용자가 직접 지정한 적이 없어 unavailableCoupons에 넣지 않는다.
     */
    private CouponResolution autoResolveCoupons(UUID memberId, UUID projectId, long rewardAmount, long shippingFee) {
        List<CouponIssuance> issuances = couponIssuanceRepository
                .findByOwnerId(memberId, CouponIssuanceStatus.AVAILABLE, Pageable.unpaged())
                .getContent();
        if (issuances.isEmpty()) {
            return new CouponResolution(List.of(), List.of(), 0);
        }

        Map<String, Coupon> couponsByCode = couponRepository
                .findByCouponCodeIn(issuances.stream().map(CouponIssuance::getCouponCode).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Coupon::getCouponCode, Function.identity()));
        ProjectMatchContext matchContext = buildMatchContext(projectId, couponsByCode.values());

        Map<IssuerType, AppliedCoupon> bestByIssuer = new EnumMap<>(IssuerType.class);
        for (CouponIssuance issuance : issuances) {
            Coupon coupon = couponsByCode.get(issuance.getCouponCode());
            if (coupon == null) {
                continue;
            }
            resolveSingleCoupon(coupon, issuance, projectId, rewardAmount, shippingFee, matchContext)
                    .ifPresent(resolved -> bestByIssuer.merge(coupon.getIssuerType(),
                            new AppliedCoupon(issuance.getId(), issuance.getCouponCode(), coupon.getIssuerType(),
                                    coupon.getDiscountType(), resolved.discount()),
                            (current, candidate) -> candidate.discountAmount() > current.discountAmount() ? candidate : current));
        }

        List<AppliedCoupon> applied = List.copyOf(bestByIssuer.values());
        long totalDiscount = applied.stream().mapToLong(AppliedCoupon::discountAmount).sum();
        return new CouponResolution(applied, List.of(), totalDiscount);
    }

    /** CATEGORY/MAKER 스코프 쿠폰 후보가 있을 때만 project-service를 조회한다(불필요한 호출 회피). */
    private ProjectMatchContext buildMatchContext(UUID projectId, Collection<Coupon> candidates) {
        boolean needsCategory = candidates.stream().anyMatch(c -> c != null && c.getTargetScope() == CouponTargetScope.CATEGORY);
        boolean needsSeller = candidates.stream().anyMatch(c -> c != null && c.getTargetScope() == CouponTargetScope.MAKER);
        if (!needsCategory && !needsSeller) {
            return ProjectMatchContext.EMPTY;
        }
        String categoryMajor = needsCategory ? projectSummaryClient.getCategoryMajor(projectId).orElse(null) : null;
        UUID sellerId = needsSeller ? projectOwnershipClient.findSellerId(projectId).orElse(null) : null;
        return new ProjectMatchContext(categoryMajor, sellerId);
    }

    /** 명시적 적용 경로 — 코드+회원으로 issuance를 다시 조회해 공용 검증(아래 오버로드)에 위임한다. */
    private Optional<ResolvedCoupon> resolveSingleCoupon(UUID memberId, UUID projectId, Coupon coupon,
                                                           long rewardAmount, long shippingFee, ProjectMatchContext matchContext) {
        if (coupon == null) {
            return Optional.empty();
        }
        return couponIssuanceRepository.findByCouponCodeAndOwnerId(coupon.getCouponCode(), memberId)
                .flatMap(issuance -> resolveSingleCoupon(coupon, issuance, projectId, rewardAmount, shippingFee, matchContext));
    }

    /**
     * 공용 검증 — 이미 들고 있는 {@link CouponIssuance}를 그대로 받는다(자동추천 경로는 회원의
     * 보유 쿠폰 목록을 한 번에 조회해두고 재조회 없이 이 메서드를 바로 쓴다).
     * 할인액까지 여기서 계산해 반환한다 — 예산 체크(hasRemainingBudget)가 실제 할인액을 알아야 해서다.
     */
    private Optional<ResolvedCoupon> resolveSingleCoupon(Coupon coupon, CouponIssuance issuance, UUID projectId,
                                                           long rewardAmount, long shippingFee, ProjectMatchContext matchContext) {
        if (!issuance.isAvailable()) {
            return Optional.empty();
        }
        if (coupon.isExpired(Instant.now())) {
            return Optional.empty();
        }
        if (!coupon.meetsMinFundingAmount(rewardAmount)) {
            return Optional.empty();
        }
        if (!coupon.matchesProject(projectId, matchContext)) {
            return Optional.empty();
        }
        long discount = coupon.calculateDiscount(rewardAmount, shippingFee);
        if (!coupon.hasRemainingBudget(discount)) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedCoupon(issuance, discount));
    }

    private String unavailableReason(UUID memberId, UUID projectId, String code, Coupon coupon, long rewardAmount,
                                      long shippingFee, ProjectMatchContext matchContext) {
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
        if (!coupon.matchesProject(projectId, matchContext)) {
            return "NOT_APPLICABLE";
        }
        if (!coupon.hasRemainingBudget(coupon.calculateDiscount(rewardAmount, shippingFee))) {
            return "BUDGET_EXCEEDED";
        }
        return "NOT_APPLICABLE";
    }

    private record ResolvedCoupon(CouponIssuance issuance, long discount) {
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
