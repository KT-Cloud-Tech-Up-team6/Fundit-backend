package com.fundit.live.application.project;

import java.util.Optional;
import java.util.UUID;

/**
 * LIVE 생성 전 "본인 소유 프로젝트인지" 서버에서 검증하기 위해(S4) project-service에서
 * 판매자 id를 조회한다. 소유권 검증은 보안에 직결되므로 실패를 무시하지 않고 그대로 전파한다
 * (order-service {@code ProjectOwnershipClient}와 같은 판단).
 *
 * <p>프로젝트가 없으면 {@link Optional#empty()}다 — 호출 측이 404로 바꾼다.
 */
public interface ProjectOwnershipClient {

    Optional<UUID> findSellerId(UUID projectId);
}
