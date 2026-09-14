package com.fundit.notification.infrastructure.persistence.notification;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * read_at은 TIMESTAMPTZ(마이크로초)라 나노초는 저장 시 잘린다. 메모리 값도 같이 잘라두지 않으면
 * "방금 쓴 값"과 "DB에서 읽은 값"이 달라져, NOTI-005 응답이 첫 호출과 이후 조회에서 다른 문자열이 된다.
 *
 * <p>이 단언을 엔티티 단위로 두는 이유: 같은 회귀를 NotificationServiceConcurrencyTest도 잡지만
 * 그건 플랫폼 클럭에 기댄다(Linux는 나노초라 잡히고, macOS는 이미 마이크로초라 고치기 전에도 통과한다).
 * 실제로 그 차이 때문에 로컬 통과 / CI 실패로 한 번 놓쳤다. 나노초 값을 직접 넣으면 어디서든 잡힌다.
 */
class NotificationJpaEntityUnitTest {

    private NotificationJpaEntity unreadNotification() {
        return NotificationJpaEntity.builder()
                .eventId("evt-1").memberId(UUID.randomUUID()).notifType(NotifType.LIVE_START)
                .title("「무선 이어폰 프로젝트」 LIVE가 시작됐어요").relatedUrl("/live/abc")
                .build();
    }

    @Test
    void 읽은_시각은_마이크로초로_잘려_저장된다() {
        // given
        NotificationJpaEntity notification = unreadNotification();

        // when
        notification.markRead(Instant.parse("2026-09-03T10:20:00.123456789Z"));

        // then
        assertThat(notification.getReadAt()).isEqualTo(Instant.parse("2026-09-03T10:20:00.123456Z"));
    }

    @Test
    void 이미_읽은_알림은_기존_시각을_유지한다() {
        // given
        NotificationJpaEntity notification = unreadNotification();
        notification.markRead(Instant.parse("2026-09-03T10:20:00Z"));

        // when
        notification.markRead(Instant.parse("2026-09-03T11:00:00Z"));

        // then
        assertThat(notification.getReadAt()).isEqualTo(Instant.parse("2026-09-03T10:20:00Z"));
    }
}
