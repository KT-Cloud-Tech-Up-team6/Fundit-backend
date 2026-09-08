package com.fundit.order.application.member;

import java.util.UUID;

/**
 * ORDER-001 — 서포터 활동 목록에서 표시명/금액 공개 여부는 member-service가 소유한 공개설정
 * 값이다(OrderDomainApiSpec.md #1 "[정책 확인 필요]"). member-service 연동 전까지는
 * {@code NoopMemberDisclosureClient}가 항상 비공개(false)로 응답해 개인정보를 안전한 방향
 * (마스킹)으로 기본 처리한다(security.md S9) — project-service의 NoopInventoryQueryClient와
 * 동일한 "연동 전 placeholder" 패턴.
 */
public interface MemberDisclosureClient {

    boolean isPublicConsent(UUID memberId);
}
