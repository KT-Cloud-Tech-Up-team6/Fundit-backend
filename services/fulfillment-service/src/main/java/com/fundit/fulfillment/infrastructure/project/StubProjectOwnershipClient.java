package com.fundit.fulfillment.infrastructure.project;

import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * project-service 연동이 비활성 모드(stub)일 때 쓰는 개발/테스트용 구현체.
 * 식별자를 시드로 결정적(deterministic) 값을 만든다 — 실제 로그인 사용자와는 무관하므로,
 * 소유권 검증이 걸린 판매자 API를 로컬에서 끝까지 확인하려면 컨트롤러 테스트처럼
 * 이 포트 자체를 목킹해야 한다.
 */
@Component
@ConditionalOnProperty(prefix = "project.integration.ownership-client", name = "mode",
        havingValue = "stub", matchIfMissing = true)
public class StubProjectOwnershipClient implements ProjectOwnershipClient {

    private static final Logger log = LoggerFactory.getLogger(StubProjectOwnershipClient.class);

    @Override
    public UUID getSellerId(Long projectId) {
        log.warn("[STUB] project-service 내부 API 미구현 — 고정값으로 대체합니다. projectId={}", projectId);
        return new UUID(1L, projectId);
    }

    @Override
    public UUID getPublicId(Long projectId) {
        log.warn("[STUB] project-service 내부 API 미구현 — 고정값으로 대체합니다. projectId={}", projectId);
        return new UUID(3L, projectId);
    }

    @Override
    public UUID getSellerId(UUID projectId) {
        log.warn("[STUB] project-service 공개 API 미구현 — 고정값으로 대체합니다. projectId={}", projectId);
        return new UUID(1L, projectId.getLeastSignificantBits());
    }

    @Override
    public UUID getPublicId(UUID projectId) {
        log.warn("[STUB] project-service 공개 API 미구현 — 고정값으로 대체합니다. projectId={}", projectId);
        return projectId;
    }
}
