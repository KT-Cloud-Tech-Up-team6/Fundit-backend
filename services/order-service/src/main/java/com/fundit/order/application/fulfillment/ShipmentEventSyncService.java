package com.fundit.order.application.fulfillment;

import com.fundit.order.domain.funding.FundingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** #129 — 판매자 발송목록 발송상태 필터·건수 캐시(fundings.shipped_at) 동기화. */
@Service
@RequiredArgsConstructor
public class ShipmentEventSyncService implements ShipmentEventListener {

    private final FundingRepository fundingRepository;

    @Override
    @Transactional
    public void onShipmentShipped(ShipmentShippedEvent event) {
        fundingRepository.markShipped(event.fundingId(), event.shippedAt());
    }
}
