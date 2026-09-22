package com.fundit.project.application.notice;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeJpaEntity;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 새소식의 제목/본문을 동시에 각각 수정해도 findByIdForUpdate(PESSIMISTIC_WRITE)로
 * 직렬화되어 두 변경 모두 유실 없이 반영되는지 검증한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class NoticeServiceConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private NoticeService noticeService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectNoticeJpaRepository noticeJpaRepository;

    @Test
    void 제목_본문을_동시에_수정해도_두_변경_모두_반영된다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        Instant now = Instant.now();
        Project project = projectRepository.save(Project.builder()
                .publicId(UUID.randomUUID()).sellerId(sellerId).status(ProjectStatus.ONGOING)
                .createdAt(now).updatedAt(now).build());
        Long noticeId = noticeService.create(sellerId, project.getPublicId(), "FAQ", "원본제목", "원본내용").getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        // when
        executor.submit(() -> {
            ready.countDown();
            await(start);
            noticeService.update(sellerId, noticeId, "새제목", null);
        });
        executor.submit(() -> {
            ready.countDown();
            await(start);
            noticeService.update(sellerId, noticeId, null, "새내용");
        });
        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // then
        ProjectNoticeJpaEntity notice = noticeJpaRepository.findById(noticeId).orElseThrow();
        assertThat(notice.getTitle()).isEqualTo("새제목");
        assertThat(notice.getContent()).isEqualTo("새내용");
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
