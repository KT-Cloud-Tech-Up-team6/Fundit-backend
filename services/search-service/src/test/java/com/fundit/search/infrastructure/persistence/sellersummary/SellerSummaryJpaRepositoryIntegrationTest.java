package com.fundit.search.infrastructure.persistence.sellersummary;

import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaEntity;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * seller_summary는 물리 테이블이 아니라 project_documents를 seller_id로 집계한 DB 뷰다
 * (SearchERD.md 7번) — Hibernate @Immutable 매핑이 실제로 뷰를 읽어내는지는 실제 Postgres에서만 검증된다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
@Transactional
class SellerSummaryJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ProjectDocumentJpaRepository projectDocumentJpaRepository;
    @Autowired
    private SellerSummaryJpaRepository sellerSummaryJpaRepository;

    @Test
    void 판매자당_프로젝트_건수를_집계한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        projectDocumentJpaRepository.save(project(1L, sellerId, "프라이팬장인", ProjectDocumentStatus.ONGOING));
        projectDocumentJpaRepository.save(project(2L, sellerId, "프라이팬장인", ProjectDocumentStatus.SUCCEEDED));

        // when
        var result = sellerSummaryJpaRepository.searchByKeyword("프라이팬장인", PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).hasSize(1);
        var seller = result.getContent().getFirst();
        assertThat(seller.getSellerId()).isEqualTo(sellerId);
        assertThat(seller.getOngoingProjectCount()).isEqualTo(1L);
        assertThat(seller.getTotalProjectCount()).isEqualTo(2L);
    }

    private ProjectDocumentJpaEntity project(long id, UUID sellerId, String sellerDisplayName, ProjectDocumentStatus status) {
        return ProjectDocumentJpaEntity.builder()
                .projectId(id)
                .projectPublicId(UUID.randomUUID())
                .sellerId(sellerId)
                .sellerDisplayName(sellerDisplayName)
                .title("타이틀" + id)
                .categoryMajor("테크·가전")
                .categoryMinor("생활가전")
                .status(status)
                .goalAmount(1_000_000L)
                .fundingDeadline(Instant.now().plus(5, ChronoUnit.DAYS))
                .projectCreatedAt(Instant.now())
                .currentAmount(0L)
                .achievementRate(0)
                .participantCount(0)
                .wishCount(0)
                .indexedAt(Instant.now())
                .build();
    }
}
