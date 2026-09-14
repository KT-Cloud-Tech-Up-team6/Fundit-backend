package com.fundit.notification.application.notification;

import com.fundit.notification.infrastructure.persistence.notification.NotifType;
import com.fundit.notification.infrastructure.persistence.notification.NotificationJpaEntity;
import com.fundit.notification.infrastructure.persistence.notification.NotificationJpaRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 예외 흐름은 {@link NotificationServiceUnitExceptionTest} 참고. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceUnitTest {

    @Mock
    private NotificationJpaRepository notificationJpaRepository;

    @InjectMocks
    private NotificationService notificationService;

    private final UUID memberId = UUID.randomUUID();

    private NotificationJpaEntity entity(Long id, Instant readAt) {
        return NotificationJpaEntity.builder()
                .id(id).eventId("evt-" + id).memberId(memberId).notifType(NotifType.SHIPPING_UPDATE)
                .title("「무선 이어폰 프로젝트」 배송이 '출고' 단계로 넘어갔어요")
                .relatedUrl("/my/fundings/1/shipping").readAt(readAt).createdAt(Instant.parse("2026-09-03T10:15:00Z"))
                .build();
    }

    @Test
    void 알림_목록은_엔티티가_아니라_항목_레코드로_변환된다() {
        // given
        when(notificationJpaRepository.findByMemberIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(entity(9001L, null))));

        // when
        var result = notificationService.getNotifications(memberId, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).singleElement()
                .satisfies(item -> {
                    assertThat(item.notificationId()).isEqualTo(9001L);
                    assertThat(item.notifType()).isEqualTo(NotifType.SHIPPING_UPDATE);
                    assertThat(item.readAt()).isNull();
                });
    }

    @Test
    void 안읽음_개수는_리포지토리_집계를_그대로_반환한다() {
        // given
        when(notificationJpaRepository.countByMemberIdAndReadAtIsNull(memberId)).thenReturn(7L);

        // when
        long unreadCount = notificationService.countUnread(memberId);

        // then
        assertThat(unreadCount).isEqualTo(7L);
    }

    @Nested
    class 읽음_처리 {

        @Test
        void 안읽은_알림이면_읽은_시각이_기록된다() {
            // given
            var notification = entity(9001L, null);
            when(notificationJpaRepository.findByIdAndMemberId(9001L, memberId)).thenReturn(Optional.of(notification));

            // when
            Instant readAt = notificationService.markRead(9001L, memberId);

            // then
            assertThat(readAt).isNotNull().isEqualTo(notification.getReadAt());
        }

        @Test
        void 이미_읽은_알림이면_기존_시각이_유지된다() {
            // given — idempotent: 재호출이 실패도 아니고 덮어쓰기도 아니다
            Instant already = Instant.parse("2026-09-03T10:20:00Z");
            when(notificationJpaRepository.findByIdAndMemberId(9001L, memberId))
                    .thenReturn(Optional.of(entity(9001L, already)));

            // when
            Instant readAt = notificationService.markRead(9001L, memberId);

            // then
            assertThat(readAt).isEqualTo(already);
        }
    }
}
