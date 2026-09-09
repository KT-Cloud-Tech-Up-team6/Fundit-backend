package com.fundit.order.application.catalog;

import java.util.Optional;
import java.util.UUID;

/**
 * ORDER-008 — 메이커 쿠폰 발급 전 "본인 소유 프로젝트인지" 서버 검증(security.md S4)을 위해
 * project-service에서 프로젝트의 판매자 id를 조회한다. 소유권 검증은 보안에 직결되므로
 * ProjectSummaryClient(제목 조회, 실패해도 degrade)와 달리 실패 시 예외를 그대로 전파한다.
 */
public interface ProjectOwnershipClient {

    Optional<UUID> findSellerId(Long projectId);
}
