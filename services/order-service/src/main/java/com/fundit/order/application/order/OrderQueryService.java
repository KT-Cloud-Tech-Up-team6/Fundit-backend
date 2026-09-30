package com.fundit.order.application.order;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.catalog.ProjectOwnershipClient;
import com.fundit.order.application.catalog.ProjectSummaryClient;
import com.fundit.order.application.fulfillment.FulfillmentStatusClient;
import com.fundit.order.application.refund.RefundStatusClient;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingProgressStage;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.funding.MemberOrderFilter;
import com.fundit.order.domain.funding.SellerOrderShippingCounts;
import com.fundit.order.domain.funding.ShippingFilter;
import com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaEntity;
import com.fundit.order.infrastructure.persistence.coupon.FundingCouponApplicationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final RefundStatusClient refundStatusClient;
    private final ProjectSummaryClient projectSummaryClient;
    private final ProjectOwnershipClient projectOwnershipClient;

    /**
     * V03 — 목록 화면이 건별로 project-service/fulfillment-service를 재호출하지 않도록, 페이지
     * 안의 프로젝트 요약·배송 상태를 한 번에 배치 조회해 각 항목에 채워 넣는다.
     */
    public Page<OrderListItem> listMyOrders(UUID memberId, MemberOrderFilter filter, Pageable pageable) {
        Page<Funding> page = fundingRepository.findByMemberId(memberId, filter, pageable);
        List<Funding> fundings = page.getContent();

        Map<UUID, ProjectSummaryClient.ProjectSummary> summaries = projectSummaryClient.getSummaries(
                fundings.stream().map(Funding::getProjectId).distinct().toList());
        List<Funding> achieved = fundings.stream().filter(f -> f.getStatus() == FundingStatus.GOAL_ACHIEVED).toList();
        Map<UUID, FulfillmentStatusClient.FulfillmentStatus> fulfillmentStatuses = fulfillmentStatusClient.fetchBatch(
                achieved.stream().map(Funding::getPublicId).toList());
        // 발송지연·진행 기록은 프로젝트 단위 판정이라 위 배치(펀딩 단위)와 경로가 다르다.
        Map<UUID, FulfillmentStatusClient.ProjectFulfillment> projectFulfillments = fulfillmentStatusClient
                .fetchProjectStatuses(achieved.stream().map(Funding::getProjectId).distinct().toList());
        Map<UUID, List<RefundStatusClient.RefundStatus>> refundStatuses = refundStatusClient.fetchBatch(
                fundings.stream().map(Funding::getPublicId).toList());
        Map<Long, Long> discountByFundingId = couponApplicationJpaRepository
                .sumDiscountAmountByFundingIdIn(fundings.stream().map(Funding::getId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        FundingCouponApplicationJpaRepository.FundingDiscountProjection::getFundingId,
                        FundingCouponApplicationJpaRepository.FundingDiscountProjection::getTotalDiscount));

        return page.map(funding -> {
            FulfillmentView view = FulfillmentView.of(fulfillmentStatuses.get(funding.getPublicId()),
                    projectFulfillments.getOrDefault(funding.getProjectId(),
                            FulfillmentStatusClient.ProjectFulfillment.NONE));
            long discountAmount = discountByFundingId.getOrDefault(funding.getId(), 0L);
            return new OrderListItem(funding, summaries.get(funding.getProjectId()), discountAmount,
                    view.availableActions(funding), view.progressStage(funding),
                    refundStatuses.getOrDefault(funding.getPublicId(), List.of()));
        });
    }

    /**
     * #129 — 판매자 발송 목록. 소유 프로젝트의 성립(GOAL_ACHIEVED) 참여 건을 검색어·발송상태
     * 필터·페이지네이션으로 조회한다. 프로젝트 소유권은 project-service 조회로 검증한다(S4).
     */
    public Page<Funding> listForSeller(UUID sellerId, UUID projectId, ShippingFilter shippingFilter, String q,
                                        Pageable pageable) {
        verifyProjectOwnership(sellerId, projectId);
        ShippingFilter filter = shippingFilter == null ? ShippingFilter.ALL : shippingFilter;
        String keyword = (q == null || q.isBlank()) ? null : q.trim();
        return fundingRepository.findSellerOrders(projectId, filter, keyword, pageable);
    }

    /** #129 — 판매자 발송 목록 탭(전체/발송대기/발송완료) 건수. */
    public SellerOrderShippingCounts sellerOrderShippingCounts(UUID sellerId, UUID projectId) {
        verifyProjectOwnership(sellerId, projectId);
        return fundingRepository.countSellerOrdersByShippingStatus(projectId);
    }

    private void verifyProjectOwnership(UUID sellerId, UUID projectId) {
        UUID actualSellerId = projectOwnershipClient.findSellerId(projectId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!actualSellerId.equals(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
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
        FulfillmentView view = resolveFulfillmentView(funding);
        ProjectSummaryClient.ProjectSummary projectSummary = projectSummaryClient
                .getSummaries(List.of(funding.getProjectId())).get(funding.getProjectId());
        return new FundingDetail(funding, discountAmount, view.availableActions(funding),
                view.progressStage(funding), projectSummary,
                refundStatusClient.fetchBatch(List.of(orderId)).getOrDefault(orderId, List.of()));
    }

    /**
     * GOAL_ACHIEVED가 아니면 배송 상태와 무관하게 결과가 같아 fulfillment-service 조회를 생략한다.
     * 목록과 달리 단건 API는 지연 여부까지 한 번에 내려주므로 프로젝트 단위 조회가 필요 없다.
     */
    private FulfillmentView resolveFulfillmentView(Funding funding) {
        if (funding.getStatus() != FundingStatus.GOAL_ACHIEVED) {
            return new FulfillmentView(false, false, null, false);
        }
        FulfillmentStatusClient.FulfillmentStatus status = fulfillmentStatusClient.fetch(funding.getPublicId());
        return new FulfillmentView(status.isAlreadyShipped(), status.isDelayed(), status.deliveredAt(),
                status.hasProgressRecord());
    }

    /** 가능 액션과 진행 단계가 같은 입력(배송 상태)을 쓰므로 한 번 모아서 두 곳에 넘긴다. */
    private record FulfillmentView(boolean isAlreadyShipped, boolean isDelayed, Instant deliveredAt,
                                    boolean hasProgressRecord) {

        /**
         * 조회 실패(값 없음)는 "미발송·지연 아님·기록 없음"으로 본다 — 누를 수 없는 버튼이나 근거
         * 없는 "제작 중" 배지를 보여주지 않는 쪽.
         */
        static FulfillmentView of(FulfillmentStatusClient.FulfillmentStatus status,
                                   FulfillmentStatusClient.ProjectFulfillment project) {
            boolean shipped = status != null && status.isAlreadyShipped();
            return new FulfillmentView(shipped, !shipped && project.isDelayed(),
                    status == null ? null : status.deliveredAt(), project.hasProgressRecord());
        }

        List<String> availableActions(Funding funding) {
            return funding.availableActions(isAlreadyShipped, isDelayed, deliveredAt);
        }

        FundingProgressStage progressStage(Funding funding) {
            return funding.progressStage(isAlreadyShipped, isDelayed, deliveredAt, hasProgressRecord);
        }
    }

    /** {@code projectSummary}는 project-service 조회 실패 시 null일 수 있다(부가 정보, degrade). */
    public record FundingDetail(Funding funding, long discountAmount, List<String> availableActions,
                                 FundingProgressStage progressStage,
                                 ProjectSummaryClient.ProjectSummary projectSummary,
                                 List<RefundStatusClient.RefundStatus> refundRequests) {

        public long finalAmount() {
            return funding.totalRewardAmount() + funding.getShippingFee() - discountAmount;
        }
    }

    /** {@code projectSummary}는 project-service 조회 실패 시 null일 수 있다(부가 정보, degrade). */
    public record OrderListItem(Funding funding, ProjectSummaryClient.ProjectSummary projectSummary,
                                 long discountAmount, List<String> availableActions,
                                 FundingProgressStage progressStage,
                                 List<RefundStatusClient.RefundStatus> refundRequests) {
    }
}
