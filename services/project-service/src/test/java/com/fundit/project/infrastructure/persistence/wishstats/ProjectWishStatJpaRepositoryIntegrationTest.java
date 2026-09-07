package com.fundit.project.infrastructure.persistence.wishstats;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 찜 집계 ON CONFLICT/DELETE 멱등이 실제 Postgres에서 중복 가감을 막는지 검증한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ProjectWishStatJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ProjectWishStatJpaRepository wishStatJpaRepository;
    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void 같은_회원_찜을_두번_반영해도_카운트는_1이다() {
        // given
        Long projectId = persistProjectId();
        UUID memberId = UUID.randomUUID();

        // when
        assertThat(wishStatJpaRepository.insertMemberIfAbsent(projectId, memberId)).isEqualTo(1);
        wishStatJpaRepository.incrementOrCreate(projectId);
        assertThat(wishStatJpaRepository.insertMemberIfAbsent(projectId, memberId)).isZero();

        // then
        assertThat(wishStatJpaRepository.findById(projectId).orElseThrow().getWishCount()).isEqualTo(1);
    }

    @Test
    void 찜_해제_후_다시_해제해도_카운트는_0이다() {
        // given
        Long projectId = persistProjectId();
        UUID memberId = UUID.randomUUID();
        assertThat(wishStatJpaRepository.insertMemberIfAbsent(projectId, memberId)).isEqualTo(1);
        wishStatJpaRepository.incrementOrCreate(projectId);

        // when
        assertThat(wishStatJpaRepository.deleteMember(projectId, memberId)).isEqualTo(1);
        wishStatJpaRepository.decrementIfPresent(projectId);
        assertThat(wishStatJpaRepository.deleteMember(projectId, memberId)).isZero();

        // then
        assertThat(wishStatJpaRepository.findById(projectId).orElseThrow().getWishCount()).isZero();
    }

    private Long persistProjectId() {
        Instant now = Instant.now();
        return projectRepository.save(Project.builder()
                .publicId(UUID.randomUUID()).sellerId(UUID.randomUUID()).status(ProjectStatus.ONGOING)
                .createdAt(now).updatedAt(now).build()).getId();
    }
}
