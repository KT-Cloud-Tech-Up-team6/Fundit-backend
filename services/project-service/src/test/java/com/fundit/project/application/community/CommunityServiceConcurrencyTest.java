package com.fundit.project.application.community;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.community.CommunityAnswerJpaRepository;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaEntity;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 게시글에 답변 UPSERT가 동시에 들어오면, 유니크 제약(uq_community_answers_post)
 * 위반이 호출부로 새지 않고 한 행만 남는지 검증한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CommunityServiceConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private CommunityService communityService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private CommunityPostJpaRepository postJpaRepository;
    @Autowired
    private CommunityAnswerJpaRepository answerJpaRepository;

    @Test
    void 같은_게시글에_동시에_답변해도_유니크제약_예외가_발생하지_않는다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        Instant now = Instant.now();
        Long projectId = projectRepository.save(Project.builder()
                .publicId(UUID.randomUUID()).sellerId(sellerId).status(ProjectStatus.ONGOING)
                .createdAt(now).updatedAt(now).build()).getId();
        Long postId = postJpaRepository.save(CommunityPostJpaEntity.builder()
                .projectId(projectId).memberId(UUID.randomUUID()).postType("QUESTION")
                .content("질문").build()).getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        Runnable upsert = () -> {
            ready.countDown();
            try {
                start.await();
                communityService.upsertAnswer(sellerId, postId, "동시 답변");
                succeeded.incrementAndGet();
            } catch (Exception e) {
                failed.incrementAndGet();
            }
        };

        // when
        executor.submit(upsert);
        executor.submit(upsert);
        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // then
        assertThat(failed.get()).isZero();
        assertThat(succeeded.get()).isEqualTo(2);
        assertThat(answerJpaRepository.findByPostIdIn(List.of(postId))).hasSize(1);
    }
}
