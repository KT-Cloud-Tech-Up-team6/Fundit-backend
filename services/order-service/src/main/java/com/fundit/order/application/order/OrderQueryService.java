package com.fundit.order.application.order;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.catalog.ProjectSummaryClient;
import com.fundit.order.application.fulfillment.FulfillmentStatusClient;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** ORDER-004(내 펀딩 참여 목록)/ORDER-005(개별 참여 상세) 조회 전용. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final FundingRepository fundingRepository;
    private final FundingCouponApplicationJpaRepository couponApplicationJpaRepository;
    private final FulfillmentStatusClient fulfillmentStatusClient;
    private final ProjectSummaryClient projectSummaryClient;

    /**
     * V03 — 목록 화면이 건별로 project-service/fulfillment-service를 재호출하지 않도록, 페이지
     * 안의 프로젝트 요약·배송 상태를 한 번에 배치 조회해 각 항목에 채워 넣는다.
     */
    public Page<OrderListItem> listMyOrders(UUID memberId, FundingStatus status, Pageable pageable) {
        Page<Funding> page = fundingRepository.findByMemberId(memberId, status, pageable);
        List<Funding> fundings = page.getContent();

        Map<UUID, ProjectSummaryClient.ProjectSummary> summaries = projectSummaryClient.getSummaries(
                fundings.stream().map(Funding::getProjectId).distinct().toList());
        Map<UUID, FulfillmentStatusClient.FulfillmentStatus> fulfillmentStatuses = fulfillmentStatusClient.fetchBatch(
                fundings.stream().filter(f -> f.getStatus() == FundingStatus.GOAL_ACHIEVED)
                        .map(Funding::getPublicId).toList());

        return page.map(funding -> {
            FulfillmentStatusClient.FulfillmentStatus fulfillmentStatus = fulfillmentStatuses.get(funding.getPublicId());
            List<String> availableActions = funding.availableActions(
                    fulfillmentStatus != null && fulfillmentStatus.isAlreadyShipped(),
                    fulfillmentStatus != null && fulfillmentStatus.isDelivered());
            return new OrderListItem(funding, summaries.get(funding.getProjectId()), availableActions);
        });
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
        List<String> availableActions = resolveAvailableActions(funding);
        return new FundingDetail(funding, discountAmount, availableActions);
    }

    /** GOAL_ACHIEVED가 아니면 배송 상태와 무관하게 결과가 같아 fulfillment-service 조회를 생략한다. */
    private List<String> resolveAvailableActions(Funding funding) {
        if (funding.getStatus() != FundingStatus.GOAL_ACHIEVED) {
            return funding.availableActions(false, false);
        }
        FulfillmentStatusClient.FulfillmentStatus status = fulfillmentStatusClient.fetch(funding.getPublicId());
        return funding.availableActions(status.isAlreadyShipped(), status.isDelivered());
    }

    public record FundingDetail(Funding funding, long discountAmount, List<String> availableActions) {

        public long finalAmount() {
            return funding.totalRewardAmount() + funding.getShippingFee() - discountAmount;
        }
    }

    /** {@code projectSummary}는 project-service 조회 실패 시 null일 수 있다(부가 정보, degrade). */
    public record OrderListItem(Funding funding, ProjectSummaryClient.ProjectSummary projectSummary,
                                 List<String> availableActions) {
    }
}
