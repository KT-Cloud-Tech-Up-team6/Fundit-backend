package com.fundit.notification.infrastructure.persistence.notification;

import com.fundit.notification.application.notification.NotificationAppendService;
import com.fundit.notification.application.notification.NotificationEventListener.NotificationRaisedEvent;
import com.fundit.notification.application.setting.NotificationSettingService;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 적재 경로(NOTI-006)의 멱등성은 실제 Postgres UNIQUE 제약이 걸려야만 검증된다 —
 * 컨슈머에 중복 판정 로직을 따로 짜지 않고 uq_notifications_event_member에 맡겼기 때문에,
 * 이 제약이 실제로 동작하는지가 곧 요구사항 충족 여부다.
 *
 * <p>@Modifying 커스텀 쿼리는 SimpleJpaRepository 기본 CRUD와 달리 자동으로 트랜잭션이 걸리지 않아
 * 테스트 클래스에 @Transactional을 둔다.
 *
 * <p>internal-api.key를 @TestPropertySource로 고정하는 이유: 공통 application.yml의
 * spring.profiles.active=local이 gitignore된 application-local.yml을 가리켜서, CI 체크아웃 트리에
 * 그 파일이 없으면 전체 컨텍스트 로딩이 PlaceholderResolutionException으로 실패한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
@Transactional
class NotificationJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private NotificationJpaRepository notificationJpaRepository;
    @Autowired
    private NotificationAppendService notificationAppendService;
    @Autowired
    private NotificationSettingService notificationSettingService;

    private NotificationRaisedEvent event(String eventId, UUID memberId) {
        return new NotificationRaisedEvent(eventId, memberId, NotifType.PROJECT_OPEN,
                "찜하신 「무선 이어폰 프로젝트」 펀딩이 오픈했어요", "/projects/1");
    }

    @Test
    void 같은_이벤트를_같은_수신자에게_두번_적재해도_한_행만_남는다() {
        // given
        UUID memberId = UUID.randomUUID();

        // when — Kafka는 at-least-once라 중복 수신은 예정된 일이다
        notificationAppendService.onNotificationRaised(event("evt-1", memberId));
        notificationAppendService.onNotificationRaised(event("evt-1", memberId));

        // then
        assertThat(notificationJpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId, PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void 같은_이벤트라도_수신자가_다르면_각각_적재된다() {
        // given — PROJECT_OPEN처럼 이벤트 하나가 수신자 여러 명으로 팬아웃되는 경우
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        // when
        notificationAppendService.onNotificationRaised(event("evt-2", first));
        notificationAppendService.onNotificationRaised(event("evt-2", second));

        // then
        assertThat(notificationJpaRepository.countByMemberIdAndReadAtIsNull(first)).isEqualTo(1);
        assertThat(notificationJpaRepository.countByMemberIdAndReadAtIsNull(second)).isEqualTo(1);
    }

    @Test
    void 수신_거부한_유형은_적재되지_않는다() {
        // given
        UUID memberId = UUID.randomUUID();
        notificationSettingService.setEnabled(memberId, NotifType.PROJECT_OPEN, false);

        // when
        notificationAppendService.onNotificationRaised(event("evt-3", memberId));

        // then
        assertThat(notificationJpaRepository.countByMemberIdAndReadAtIsNull(memberId)).isZero();
    }

    @Test
    void 타인의_알림_ID로는_조회되지_않는다() {
        // given
        UUID owner = UUID.randomUUID();
        notificationAppendService.onNotificationRaised(event("evt-4", owner));
        Long notificationId = notificationJpaRepository
                .findByMemberIdOrderByCreatedAtDesc(owner, PageRequest.of(0, 1))
                .getContent().getFirst().getId();

        // when & then — "없음"과 구별되지 않아야 404가 나온다(security.md S10)
        assertThat(notificationJpaRepository.findByIdAndMemberId(notificationId, UUID.randomUUID())).isEmpty();
        assertThat(notificationJpaRepository.findByIdAndMemberId(notificationId, owner)).isPresent();
    }
}
