package com.fundit.notification.application.notification;

import com.fundit.notification.infrastructure.persistence.notification.NotifType;
import com.fundit.notification.infrastructure.persistence.notification.NotificationJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NOTI-005의 "read_at은 최초 1회만 기록하고 덮어쓰지 않는다"가 동시 요청에서도 지켜지는지 검증한다.
 *
 * <p>PR 리뷰 지적: 잠금이 없으면 같은 알림에 PATCH가 동시에 들어왔을 때 두 트랜잭션이 모두
 * read_at=null을 읽고 각자 쓰므로, 나중에 커밋한 요청이 최초 시각을 덮어쓴다.
 * NotificationJpaRepository#findByIdAndMemberId의 PESSIMISTIC_WRITE로 행 단위 직렬화를 건 뒤,
 * 어느 요청이 이기든 최종 read_at이 하나로 수렴하는지 실제 DB로 확인한다.
 *
 * <p>클래스에 @Transactional을 걸지 않는다 — 테스트 트랜잭션 하나에 묶이면 동시성이 재현되지 않는다.
 * internal-api.key 고정 이유는 다른 통합 테스트와 같다(application-local.yml이 .gitignore 대상이라
 * CI 체크아웃 트리에 없어 컨텍스트 로딩이 실패한다).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class NotificationServiceConcurrencyTest {

    private static final int CONCURRENCY = 8;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationAppendService notificationAppendService;
    @Autowired
    private NotificationJpaRepository notificationJpaRepository;

    @Test
    void 같은_알림을_동시에_읽음_처리해도_최초_시각이_덮어써지지_않는다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        notificationAppendService.onNotificationRaised(new NotificationEventListener.NotificationRaisedEvent(
                "evt-concurrency-" + UUID.randomUUID(), memberId, NotifType.LIVE_START,
                "「무선 이어폰 프로젝트」 LIVE가 시작됐어요", "/live/abc"));
        Long notificationId = notificationJpaRepository
                .findByMemberIdOrderByCreatedAtDesc(memberId, PageRequest.of(0, 1))
                .getContent().getFirst().getId();

        List<Instant> returned = Collections.synchronizedList(new java.util.ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENCY);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);

        // when — 8개 요청이 같은 알림을 동시에 읽음 처리한다
        for (int i = 0; i < CONCURRENCY; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    returned.add(notificationService.markRead(notificationId, memberId));
                } catch (Exception ignored) {
                    // 실패한 요청은 아래 단언에서 건수로 드러난다
                } finally {
                    done.countDown();
                }
            });
        }
        // await 단언이 실패해도 풀이 남지 않도록 finally에서 정리한다.
        // try-with-resources(ExecutorService는 AutoCloseable)를 쓰지 않는 이유: close()는 종료까지
        // 블로킹이라 스레드가 DB 락에 걸려 있으면 스위트 전체가 멈춘다. shutdownNow는 인터럽트만 걸고 반환한다.
        try {
            start.countDown();
            assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        // then — 모든 요청이 같은 시각을 돌려받고, 그 값이 DB에 저장된 값과 일치한다.
        // 확인용 읽기는 findById를 쓴다 — findByIdAndMemberId는 PESSIMISTIC_WRITE라 트랜잭션이 필요하다.
        Instant persisted = notificationJpaRepository.findById(notificationId).orElseThrow().getReadAt();
        assertThat(returned).hasSize(CONCURRENCY);
        assertThat(Set.copyOf(returned)).as("덮어쓰기가 일어나면 서로 다른 시각이 섞인다").hasSize(1);
        assertThat(returned.getFirst()).isEqualTo(persisted);

        // 안읽음 개수도 0으로 수렴한다
        assertThat(notificationJpaRepository.countByMemberIdAndReadAtIsNull(memberId)).isZero();
    }
}
