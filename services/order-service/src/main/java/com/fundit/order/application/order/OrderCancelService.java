package com.fundit.order.application.order;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.funding.FundingEventPublisher;
import com.fundit.order.domain.funding.CancelReason;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.inventory.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * ORDER-014 — 참여 취소(단순변심). 마감 전(PENDING/FUNDING_IN_PROGRESS)에만 가능하며,
 * 취소 시 차감했던 재고를 원복하고 FundingCancelledByMember 이벤트를 발행한다
 * (실제 결제취소/환불 실행은 payment-service가 이 이벤트를 구독해서 처리).
 */
@Service
@RequiredArgsConstructor
public class OrderCancelService {

    private final FundingRepository fundingRepository;
    private final InventoryRepository inventoryRepository;
    private final FundingEventPublisher fundingEventPublisher;

    @Transactional
    public Funding cancel(UUID memberId, UUID orderId, CancelReason cancelReason, String cancelReasonDetail) {
        Funding funding = fundingRepository.findByPublicId(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!funding.isOwnedBy(memberId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        // 취소 불가 상태면 여기서 BusinessException을 던진다. 사유는 기록용이라 취소 가능 여부를 바꾸지 않는다.
        funding.cancelByMember(cancelReason, cancelReasonDetail);

        for (FundingLineItem lineItem : funding.getLineItems()) {
            inventoryRepository.increaseStock(lineItem.rewardId(), lineItem.quantity());
        }

        Funding saved = fundingRepository.save(funding);

        // FundingCancelledByMemberEvent.projectId는 이벤트 계약(Long, project-service 내부 PK)이 그대로인데
        // Funding은 cross-service ID 통일(#69) 이후 UUID만 들고 있어 값을 채울 수 없다 — 알려진 한계로
        // null로 발행한다(payment-service PAYMENT-004는 fundingId만으로 결제를 조회해 처리하므로 영향 없음).
        // 취소 사유는 이벤트 계약(=아웃박스 행)에 담지 않는다 — 발행 시점에 KafkaFundingEventTransport가
        // 이미 조회하는 fundings 행에 그대로 있고, 취소된 뒤로는 바뀌지 않는 값이다.
        fundingEventPublisher.publishFundingCancelledByMember(
                new FundingEventPublisher.FundingCancelledByMemberEvent(saved.getId(), null, memberId));

        return saved;
    }
}
