package com.fundit.order.application.order;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.funding.FundingEventPublisher;
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
    public Funding cancel(UUID memberId, UUID orderId) {
        Funding funding = fundingRepository.findByPublicId(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!funding.isOwnedBy(memberId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        funding.cancelByMember(); // 취소 불가 상태면 여기서 BusinessException을 던진다.

        for (FundingLineItem lineItem : funding.getLineItems()) {
            inventoryRepository.increaseStock(lineItem.rewardId(), lineItem.quantity());
        }

        Funding saved = fundingRepository.save(funding);

        fundingEventPublisher.publishFundingCancelledByMember(
                new FundingEventPublisher.FundingCancelledByMemberEvent(saved.getId(), saved.getProjectId(), memberId));

        return saved;
    }
}
