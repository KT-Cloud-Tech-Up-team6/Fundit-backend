package com.fundit.project.application.project;

import java.util.Optional;
import java.util.UUID;

/**
 * 판매자 표시명은 member-service 소관이라 조회해 가져와야 한다(PROJECT-020의 seller.displayName).
 * member-service 간 동기 호출이 아직 이 서비스에 구성되지 않아, 이 슬라이스에서는 항상 빈 값을
 * 반환하는 스텁 구현체({@code NoopSellerProfileClient})만 둔다 — RewardEventPublisher/
 * InventoryQueryClient와 동일한 성격의 placeholder.
 */
public interface SellerProfileClient {

    Optional<String> getDisplayName(UUID sellerId);
}
