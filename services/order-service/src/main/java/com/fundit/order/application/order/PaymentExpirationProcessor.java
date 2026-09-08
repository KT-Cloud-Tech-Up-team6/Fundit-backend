package com.fundit.order.application.order;

import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.inventory.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORDER-013 — 미결제 주문 한 건을 만료 처리한다. 배치 대상 건마다 별도 트랜잭션으로 처리해,
 * 한 건에서 실패해도(재고 원복 실패 등) 이미 처리된 다른 건이 함께 롤백되지 않게 한다
 * ("배치 실행 중 재고 원복 실패 → 로그 기록 후 재시도 대상으로 표시" 요구사항).
 */
@Service
@RequiredArgsConstructor
public class PaymentExpirationProcessor {

    private final FundingRepository fundingRepository;
    private final InventoryRepository inventoryRepository;

    /** @return true면 이번 호출로 만료 처리됨, false면 이미 처리돼 있었음(idempotent). */
    @Transactional
    public boolean expireOne(Long fundingId) {
        Funding funding = fundingRepository.findById(fundingId).orElse(null);
        if (funding == null) {
            return false;
        }
        boolean expired = funding.expireIfPending();
        if (!expired) {
            return false;
        }
        for (FundingLineItem lineItem : funding.getLineItems()) {
            inventoryRepository.increaseStock(lineItem.rewardId(), lineItem.quantity());
        }
        fundingRepository.save(funding);
        return true;
    }
}
