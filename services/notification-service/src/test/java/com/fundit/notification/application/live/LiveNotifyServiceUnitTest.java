package com.fundit.notification.application.live;

import com.fundit.notification.infrastructure.persistence.livenotifyrequest.LiveNotifyRequestJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveNotifyServiceUnitTest {

    @Mock
    private LiveNotifyRequestJpaRepository liveNotifyRequestJpaRepository;

    @InjectMocks
    private LiveNotifyService liveNotifyService;

    @Test
    void 알림을_신청하면_idempotent_insert를_호출한다() {
        // given
        UUID liveId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        // when
        liveNotifyService.requestNotify(liveId, memberId);

        // then
        verify(liveNotifyRequestJpaRepository).insertIgnoringConflict(liveId, memberId);
    }

    @Test
    void 알림을_해제하면_신청_행을_삭제한다() {
        // given
        UUID liveId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        // when
        liveNotifyService.cancelNotify(liveId, memberId);

        // then
        verify(liveNotifyRequestJpaRepository).deleteByLiveIdAndMemberId(liveId, memberId);
    }
}
