package com.fundit.order.application.order;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    /**
     * ORDER-003. {@code idempotencyKey}는 {@code Idempotency-Key} 헤더(선택값) — 없으면 항상
     * 새 주문을 만든다(기존 동작 유지). 있으면 회원 범위로 조회해 같은 키가 이미 있으면 새로
     * 만들지 않고 기존 주문을 그대로 돌려준다(재고 차감/쿠폰 적용을 다시 하지 않음). 같은 키에
     * 요청 본문(idempotencyRequestHash)이 다르면 CONFLICT로 거부한다.
     */
    @Transactional
    public OrderCreateResult create(UUID memberId, UUID projectId, List<OrderLineItemRequest> lineItemRequests,
                                     ShippingAddress shippingAddress, List<String> couponCodes, boolean autoApplyBestCoupon,
                                     String idempotencyKey, String idempotencyRequestHash) {
        if (idempotencyKey != null) {
            Optional<OrderCreateResult> replay = findReplay(memberId, idempotencyKey, idempotencyRequestHash);
            if (replay.isPresent()) {
                return replay.get();
            }
        }

        OrderPricingService.PricingResult pricing =
                orderPricingService.calculate(memberId, projectId, lineItemRequests, couponCodes, autoApplyBestCoupon);

        decreaseStockOrThrow(pricing.lineItems());

        List<FundingLineItem> fundingLineItems = pricing.lineItems().stream()
                .map(this::toFundingLineItem)
                .toList();

        String projectTitle = projectSummaryClient.getProjectTitle(projectId).orElse("");
        Instant paymentExpiresAt = Instant.now().plus(paymentExpiryMinutes, ChronoUnit.MINUTES);

        Funding funding = Funding.create(memberId, projectId, projectTitle, shippingAddress,
                pricing.shippingFee(), fundingLineItems, paymentExpiresAt, idempotencyKey, idempotencyRequestHash);
        Funding saved;
        try {
            saved = fundingRepository.save(funding);
        } catch (DataIntegrityViolationException e) {
            // uq_fundings_member_idempotency_key 위반 — 동시에 같은 키로 들어온 다른 요청이 먼저
            // 커밋됨. 이 트랜잭션은 이미 실패했으니(Postgres는 문장 실패 후 같은 트랜잭션 재사용 불가)
            // CONFLICT로 응답하고, 클라이언트가 같은 키로 재시도하면 이번엔 위 findReplay가 잡는다.
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "동일한 Idempotency-Key로 처리 중인 요청이 있습니다. 잠시 후 다시 시도하세요.");
        }

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
            // 위 OrderPricingService.resolveSingleCoupon()이 방금 hasRemainingBudget()로 확인했지만,
            // 그 확인과 이 조건부 UPDATE 사이의 레이스로 예산이 그새 소진될 수 있다 — 그 경우 회계가
            // 틀어진 채 주문만 조용히 성공하지 않도록 주문 생성 자체를 실패시킨다(재고 부족과 동일 층위).
            boolean budgetReserved = couponRepository.increaseUsedBudget(applied.couponCode(), applied.discountAmount());
            if (!budgetReserved) {
                throw new BusinessException(OrderErrorCode.COUPON_BUDGET_EXCEEDED,
                        "쿠폰 예산이 소진되었습니다. couponCode=" + applied.couponCode());
            }
        }

        return new OrderCreateResult(saved, pricing.finalAmount(), false);
    }

    /** 같은 회원의 같은 키로 이미 만든 주문이 있으면 재고 차감/쿠폰 적용 없이 그대로 돌려준다. */
    private Optional<OrderCreateResult> findReplay(UUID memberId, String idempotencyKey, String idempotencyRequestHash) {
        return fundingRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey).map(existing -> {
            if (!Objects.equals(existing.getIdempotencyRequestHash(), idempotencyRequestHash)) {
                throw new BusinessException(CommonErrorCode.CONFLICT,
                        "동일한 Idempotency-Key로 다른 내용의 요청이 감지되었습니다.");
            }
            long discountAmount = couponApplicationJpaRepository.findByFundingId(existing.getId()).stream()
                    .mapToLong(FundingCouponApplicationJpaEntity::getDiscountAmount)
                    .sum();
            long finalAmount = existing.totalRewardAmount() + existing.getShippingFee() - discountAmount;
            return new OrderCreateResult(existing, finalAmount, true);
        });
    }

    /** {@code replay=true}면 이번 호출로 새로 만든 주문이 아니라 같은 키의 기존 주문을 그대로 반환한 것이다. */
    public record OrderCreateResult(Funding funding, long finalAmount, boolean replay) {
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
