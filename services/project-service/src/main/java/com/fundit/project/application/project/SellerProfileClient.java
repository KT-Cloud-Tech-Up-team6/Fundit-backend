package com.fundit.project.application.project;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 판매자 표시명은 member-service 소관이라 조회해 가져와야 한다(PROJECT-020의 seller.displayName).
 * 구현은 member 내부 닉네임 일괄 조회({@code MemberServiceSellerProfileClient})다.
 *
 * <p><b>조회 실패는 빈 값이다</b> — 판매자명은 부가 정보라 member 장애가 프로젝트 상세·목록·색인 이벤트를
 * 막으면 안 된다(live 카드 판매자명과 같은 판단).
 */
public interface SellerProfileClient {

    /** 판매자 ID → 표시명. 조회 실패·닉네임 없는 판매자는 결과에서 빠진다. */
    Map<UUID, String> getDisplayNames(Collection<UUID> sellerIds);

    default Optional<String> getDisplayName(UUID sellerId) {
        return Optional.ofNullable(getDisplayNames(List.of(sellerId)).get(sellerId));
    }
}
