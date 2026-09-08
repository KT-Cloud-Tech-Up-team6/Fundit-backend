package com.fundit.order.application.order;

import com.fundit.common.error.BusinessException;
import com.fundit.order.application.catalog.ProjectSummaryClient;
import com.fundit.order.domain.OrderErrorCode;
import com.fundit.order.domain.coupon.CouponRepository;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
import com.fundit.order.domain.funding.FundingLineItemOption;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.ShippingAddress;
import com.fundit.order.domain.inventory.InventoryRepository;
import com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaEntity;
import com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * ORDER-003 — 펀딩 주문 생성(재고 검증/차감). 하나의 트랜잭션으로 재고 차감 + Funding 생성을
 * 처리한다 — 재고 부족 시 예외로 트랜잭션 전체가 롤백되어 이미 차감한 다른 항목의 재고도
 * 함께 원복된다(CLAUDE.md "재고 부족 시 409 CONFLICT, 트랜잭션 롤백(재고 변경 없음)").
 */
@Service
public class OrderCreateService {

    private final OrderPricingService orderPricingService;
    private final InventoryRepository inventoryRepository;
    private final FundingRepository fundingRepository;
    private final FundingCouponApplicationJpaRepository couponApplicationJpaRepository;
    private final CouponRepository couponRepository;
    private final ProjectSummaryClient projectSummaryClient;
    private final long paymentExpiryMinutes;

    public OrderCreateService(OrderPricingService orderPricingService,
                               InventoryRepository inventoryRepository,
                               FundingRepository fundingRepository,
                               FundingCouponApplicationJpaRepository couponApplicationJpaRepository,
                               CouponRepository couponRepository,
                               ProjectSummaryClient projectSummaryClient,
                               @Value("${order.policy.payment-expiry-minutes}") long paymentExpiryMinutes) {
        this.orderPricingService = orderPricingService;
        this.inventoryRepository = inventoryRepository;
        this.fundingRepository = fundingRepository;
        this.couponApplicationJpaRepository = couponApplicationJpaRepository;
        this.couponRepository = couponRepository;
        this.projectSummaryClient = projectSummaryClient;
        this.paymentExpiryMinutes = paymentExpiryMinutes;
    }

    @Transactional
    public OrderCreateResult create(UUID memberId, Long projectId, List<OrderLineItemRequest> lineItemRequests,
                                     ShippingAddress shippingAddress, List<String> couponCodes) {
        OrderPricingService.PricingResult pricing =
                orderPricingService.calculate(memberId, projectId, lineItemRequests, couponCodes);

        decreaseStockOrThrow(pricing.lineItems());

        List<FundingLineItem> fundingLineItems = pricing.lineItems().stream()
                .map(this::toFundingLineItem)
                .toList();

        String projectTitle = projectSummaryClient.getProjectTitle(projectId).orElse("");
        Instant paymentExpiresAt = Instant.now().plus(paymentExpiryMinutes, ChronoUnit.MINUTES);

        Funding funding = Funding.create(memberId, projectId, projectTitle, shippingAddress,
                pricing.shippingFee(), fundingLineItems, paymentExpiresAt);
        Funding saved = fundingRepository.save(funding);

        // 쿠폰 사용확정(coupon_issuances.status 변경)은 아직 하지 않는다 — ORDER-015가 결제완료
        // 이벤트로 처리한다. 여기서는 "이 주문에 이 쿠폰이 적용됐다"는 사실을 기록하고,
        // 쿠폰의 예산 사용액(used_budget_amount)을 함께 갱신한다(CLAUDE.md "예산 한도와 발급
        // 수량은 별개로 관리" — 정률 할인 쿠폰은 적용 시점에 예산이 먼저 소진될 수 있다).
        for (OrderPricingService.AppliedCoupon applied : pricing.appliedCoupons()) {
            couponApplicationJpaRepository.save(FundingCouponApplicationJpaEntity.builder()
                    .fundingId(saved.getId())
                    .couponIssuanceId(applied.couponIssuanceId())
                    .discountAmount(applied.discountAmount())
                    .build());
            couponRepository.increaseUsedBudget(applied.couponCode(), applied.discountAmount());
        }

        return new OrderCreateResult(saved, pricing.finalAmount());
    }

    public record OrderCreateResult(Funding funding, long finalAmount) {
    }

    private void decreaseStockOrThrow(List<OrderPricingService.ResolvedLineItem> lineItems) {
        for (OrderPricingService.ResolvedLineItem lineItem : lineItems) {
            boolean success = inventoryRepository.decreaseStock(lineItem.rewardId(), lineItem.quantity());
            if (!success) {
                // ErrorResponse.detail은 BusinessException 경로에서 항상 null이라(GlobalExceptionHandler
                // 참고) 부족한 rewardId는 메시지에 담아 노출한다.
                throw new BusinessException(OrderErrorCode.INSUFFICIENT_STOCK,
                        "재고가 부족합니다. rewardId=" + lineItem.rewardId());
            }
        }
    }

    private FundingLineItem toFundingLineItem(OrderPricingService.ResolvedLineItem lineItem) {
        List<FundingLineItemOption> options = lineItem.options().stream()
                .map(o -> new FundingLineItemOption(null, o.groupId(), o.groupName(), o.valueId(), o.value()))
                .toList();
        return new FundingLineItem(null, lineItem.rewardId(), lineItem.rewardName(), lineItem.quantity(),
                lineItem.unitPrice(), options);
    }
}
