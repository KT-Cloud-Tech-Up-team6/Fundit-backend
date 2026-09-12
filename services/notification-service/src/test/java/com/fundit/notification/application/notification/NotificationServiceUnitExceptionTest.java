package com.fundit.notification.application.notification;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.notification.infrastructure.persistence.notification.NotificationJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** 정상 흐름은 {@link NotificationServiceUnitTest} 참고. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceUnitExceptionTest {

    @Mock
    private NotificationJpaRepository notificationJpaRepository;

    @InjectMocks
    private NotificationService notificationService;

    /**
     * 타인의 알림도 "없음"과 똑같이 404가 나온다 — 리포지토리가 소유자 조건을 쿼리에 묶어
     * 두 경우 모두 Optional.empty()로 돌려주기 때문이다. 403이면 "그 알림이 존재한다"를
     * 알려주게 되어 ID를 넣어보며 타인 알림의 존재 여부를 캐낼 수 있다(security.md S10).
     */
    @Test
    void 없거나_타인의_알림을_읽음_처리하면_NOT_FOUND가_발생한다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(notificationJpaRepository.findByIdAndMemberId(9001L, memberId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> notificationService.markRead(9001L, memberId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND));
    }
}
