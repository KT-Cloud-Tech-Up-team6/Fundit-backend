package com.fundit.order.application.coupon;

import java.util.UUID;

/**
 * ORDER-007 — 신규가입/등급산정/이벤트조건에 따른 플랫폼 쿠폰 자동 발급의 인바운드 트리거.
 * member-service가 이런 생애주기 이벤트를 아직 발행하지 않아(다른 서비스에도 선례 없음)
 * 이 포트를 호출하는 실제 리스너는 없다 — member-service 이벤트 발행이 확정되면
 * infrastructure/event에 어댑터를 추가해 이 포트(→ CouponIssuanceService.autoIssue)를
 * 연결한다. 어떤 쿠폰 템플릿을 어떤 조건에 매칭할지(캠페인 규칙)는 PM 정책 확인이 필요해
 * 이번 슬라이스는 "신규가입 → 설정된 웰컴 쿠폰 1종" 케이스만 대표로 배선해뒀다.
 */
public interface MemberLifecycleEventListener {

    void onMemberSignedUp(MemberSignedUpEvent event);

    record MemberSignedUpEvent(UUID memberId) {
    }
}
