package com.fundit.order.domain.coupon;

import java.util.UUID;

/**
 * {@link Coupon#matchesProject} 검증에 필요한, project-service 조회로만 알 수 있는 값.
 * CATEGORY/MAKER 스코프 쿠폰이 없으면 호출부가 project-service를 조회하지 않고
 * {@link #EMPTY}를 넘긴다 — 흔한 ALL/PROJECT 스코프 쿠폰 경로에서 불필요한 호출을 피한다.
 */
public record ProjectMatchContext(String categoryMajor, UUID sellerId) {

    public static final ProjectMatchContext EMPTY = new ProjectMatchContext(null, null);
}
