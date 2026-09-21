package com.fundit.order.application.funding;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.catalog.ProjectOwnershipClient;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
import com.fundit.order.domain.funding.FundingLineItemOption;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaEntity;
import com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 내부 전용 조회 — payment/fulfillment-service가 동기 호출하는 필드를 제공한다.
 * payment-service {@code HttpOrderFundingClient}(PAYMENT-001)가 기대하는 계약 그대로다.
 */
@Service
@RequiredArgsConstructor
public class FundingInternalQueryService {

    private final FundingRepository fundingRepository;
    private final FundingCouponApplicationJpaRepository couponApplicationJpaRepository;
    private final ProjectOwnershipClient projectOwnershipClient;

    @Transactional(readOnly = true)
    public FundingSnapshot getSnapshot(Long fundingId) {
        Funding funding = fundingRepository.findById(fundingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return toSnapshot(funding);
    }

    @Transactional(readOnly = true)
    public FundingSnapshot getSnapshotByOrderId(UUID orderId) {
        Funding funding = fundingRepository.findByPublicId(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return toSnapshot(funding);
    }

    /** 알림 팬아웃 대상 — 펀딩 성립 후 아직 환불되지 않은 참여자의 memberId 목록. */
    @Transactional(readOnly = true)
    public List<UUID> listGoalAchievedParticipantMemberIds(UUID projectId) {
        return fundingRepository.findGoalAchievedByProjectId(projectId).stream()
                .map(Funding::getMemberId)
                .toList();
    }

    /** payment-service 환불 목록(V04) 배치 조회 — 건별 호출(N+1) 방지용. */
    @Transactional(readOnly = true)
    public List<OrderSummarySnapshot> getOrderSummaries(List<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return fundingRepository.findByPublicIdIn(orderIds).stream()
                .map(funding -> new OrderSummarySnapshot(funding.getPublicId(), funding.getProjectTitle(),
                        funding.getLineItems()))
                .toList();
    }

    private FundingSnapshot toSnapshot(Funding funding) {
        List<FundingCouponApplicationJpaEntity> couponApplications =
                couponApplicationJpaRepository.findByFundingId(funding.getId());
        long discountAmount = couponApplications.stream()
                .mapToLong(FundingCouponApplicationJpaEntity::getDiscountAmount)
                .sum();
        long finalAmount = funding.totalRewardAmount() + funding.getShippingFee() - discountAmount;
        // 한 주문에 쿠폰이 최대 2개(플랫폼+메이커) 붙을 수 있지만 이 필드는 단수다 — payment-service
        // PaymentCompletedEvent도 이미 단수로 고정돼 있어 그 계약에 맞춰 첫 번째 적용분만 노출한다
        // [알려진 제약 — 복수 적용 건의 사용확정/복원 전체 반영은 별도 이슈].
        Long couponIssuanceId = couponApplications.isEmpty() ? null : couponApplications.get(0).getCouponIssuanceId();
        UUID sellerId = projectOwnershipClient.findSellerId(funding.getProjectId()).orElse(null);

        return new FundingSnapshot(funding.getId(), funding.getProjectId(), funding.getMemberId(),
                funding.getPublicId(), sellerId, funding.getStatus().name(), finalAmount,
                orderName(funding.getLineItems()), couponIssuanceId, couponApplications.size(),
                funding.getShippingFee(), discountAmount);
    }

    /** PAYMENT-009/012 정산 집계 — 리워드·옵션별 판매 수량/금액과 메이커 쿠폰 차감액을 함께 반환한다. */
    @Transactional(readOnly = true)
    public SettlementAggregateSnapshot getSettlementAggregate(Long fundingId) {
        Funding funding = fundingRepository.findById(fundingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        List<LineItemSnapshot> lineItems = funding.getLineItems().stream()
                .map(li -> new LineItemSnapshot(li.rewardId(), li.rewardName(), optionName(li.options()),
                        li.quantity(), li.amount()))
                .toList();
        long makerCouponDeductionAmount = couponApplicationJpaRepository.sumMakerCouponDiscountAmount(fundingId);
        return new SettlementAggregateSnapshot(lineItems, makerCouponDeductionAmount);
    }

    /** 한 리워드에 여러 옵션(색상+사이즈 등)이 붙을 수 있어 표시용 문자열로 합친다. */
    private static String optionName(List<FundingLineItemOption> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        return options.stream()
                .map(o -> o.optionGroupName() + ": " + o.optionValue())
                .collect(Collectors.joining(", "));
    }

    private static String orderName(List<FundingLineItem> lineItems) {
        if (lineItems.isEmpty()) {
            return "";
        }
        String firstRewardName = lineItems.get(0).rewardName();
        return lineItems.size() == 1 ? firstRewardName : firstRewardName + " 외 " + (lineItems.size() - 1) + "건";
    }

    /**
     * {@code fundingId}는 order-service 내부 PK(레거시 연동·이벤트 상관용).
     * {@code projectId}는 project-service publicId(UUID)다. payment-service
     * {@code HttpOrderFundingClient}가 기대하는 필드 그대로다 — 필드명을 바꾸면 Jackson 매핑이 깨진다.
     */
    public record FundingSnapshot(Long fundingId, UUID projectId, UUID memberId, UUID fundingPublicId,
                                   UUID sellerId, String status, long finalAmount, String orderName,
                                   Long couponIssuanceId, int appliedCouponCount, long shippingFee, long discountAmount) {
    }

    public record OrderSummarySnapshot(UUID orderId, String projectTitle, List<FundingLineItem> lineItems) {
    }

    public record LineItemSnapshot(Long rewardId, String rewardName, String optionName, int quantity, long amount) {
    }

    public record SettlementAggregateSnapshot(List<LineItemSnapshot> lineItems, long makerCouponDeductionAmount) {
    }
}
