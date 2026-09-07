package com.fundit.project.application.reward;

import java.util.Optional;

/**
 * 잔여 재고(inventories)는 order-service 소유 원장이라 실시간 조회로 가져온다
 * (project-service CLAUDE.md 핵심 설계 결정, PROJECT-028). order-service가 아직 스캐폴딩되지
 * 않아 이 슬라이스에서는 항상 빈 값(조회 불가)을 반환하는 스텁 구현체
 * ({@code NoopInventoryQueryClient})만 둔다 — order-service가 생기면 실제 HTTP 클라이언트로 교체한다.
 *
 * 반환값이 비어있으면 컨트롤러/서비스는 remainingStock을 null로 응답한다(ApiSpec #14 "장애 시 null"
 * 규칙과 동일하게 처리 — 진짜 장애든 미연동이든 소비자 응답 관점에서는 같은 처리).
 */
public interface InventoryQueryClient {

    Optional<Integer> getRemainingStock(Long rewardId);
}
