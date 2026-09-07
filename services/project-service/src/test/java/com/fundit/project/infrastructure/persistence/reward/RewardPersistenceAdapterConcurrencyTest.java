package com.fundit.project.infrastructure.persistence.reward;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardOptionGroup;
import com.fundit.project.domain.reward.RewardRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 리워드에 옵션 치환이 동시에 들어오면 삭제-재삽입이 섞이지 않고,
 * 최종적으로 한 쪽의 옵션 집합만 남는지 검증한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RewardPersistenceAdapterConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private RewardRepository rewardRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private RewardOptionGroupJpaRepository optionGroupJpaRepository;

    @Test
    void 같은_리워드_옵션을_동시에_치환해도_한_집합만_남는다() throws Exception {
        // given
        Instant now = Instant.now();
        Long projectId = projectRepository.save(Project.builder()
                .publicId(UUID.randomUUID()).sellerId(UUID.randomUUID()).status(ProjectStatus.DRAFT)
                .createdAt(now).updatedAt(now).build()).getId();
        Long rewardId = rewardRepository.save(
                Reward.create(projectId, "얼리버드", "설명", null, 39000L, false, null, false, null)).getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Runnable replaceColor = () -> {
            ready.countDown();
            try {
                start.await();
                rewardRepository.replaceOptions(rewardId, List.of(new RewardOptionGroup("색상", List.of("화이트"))));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        Runnable replaceSize = () -> {
            ready.countDown();
            try {
                start.await();
                rewardRepository.replaceOptions(rewardId, List.of(new RewardOptionGroup("사이즈", List.of("M"))));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };

        // when
        executor.submit(replaceColor);
        executor.submit(replaceSize);
        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // then
        Set<String> groupNames = optionGroupJpaRepository.findByRewardId(rewardId).stream()
                .map(RewardOptionGroupJpaEntity::getName)
                .collect(Collectors.toSet());
        assertThat(groupNames).hasSize(1).isSubsetOf("색상", "사이즈");
    }
}
