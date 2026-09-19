package com.fundit.project.application.reward;

import java.util.Optional;

/**
 * 잔여 재고(inventories)는 order-service 소유 원장이라 실시간 조회로 가져온다
 * (project-service CLAUDE.md 핵심 설계 결정, PROJECT-028). 실제 구현체는 order-service의
 * {@code GET /api/v1/inventories/{rewardId}}를 호출하는 {@code HttpInventoryQueryClient}이고,
 * order-service 연동을 끄고 싶을 때(로컬 개발 등)는 {@code order.integration.inventory-client.mode=stub}로
 * {@code StubInventoryQueryClient}를 대신 쓴다.
 *
 * 반환값이 비어있으면 컨트롤러/서비스는 remainingStock을 null로 응답한다(ApiSpec #14 "장애 시 null"
 * 규칙과 동일하게 처리 — 진짜 장애든 미연동이든 소비자 응답 관점에서는 같은 처리).
 */
public interface InventoryQueryClient {

    Optional<Integer> getRemainingStock(Long rewardId);
}
