package com.fundit.live.application.session;

import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.event.LiveEventOutboxJpaRepository;
import com.fundit.live.domain.session.LiveStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LiveStreamServiceUnitTest {

    @Mock private LiveSessionRepository sessionRepository;
    @Mock private LiveEventOutboxJpaRepository outboxRepository;
    @Mock private IvsClient ivsClient;

    @InjectMocks private LiveStreamService liveStreamService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    @Test
    void 시작하면_LIVE로_전이한다() {
        // given
        LiveSession session = LiveSession.create(1L, UUID.randomUUID());
        given(sessionRepository.findOwnedForUpdate(liveId, sellerId)).willReturn(Optional.of(session));
        given(sessionRepository.save(any(LiveSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        LiveSession started = liveStreamService.start(sellerId, liveId);

        // then
        assertThat(started.getStatus()).isEqualTo(LiveStatus.LIVE);
        assertThat(started.getActualStartAt()).isNotNull();
    }

    @Test
    void 종료하면_ENDED로_전이한다() {
        // given
        LiveSession session = LiveSession.create(1L, UUID.randomUUID());
        session.start(Instant.parse("2026-09-10T11:00:00Z"), "arn:chat");
        given(sessionRepository.findOwnedForUpdate(liveId, sellerId)).willReturn(Optional.of(session));
        given(sessionRepository.save(any(LiveSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        LiveSession ended = liveStreamService.end(sellerId, liveId);

        // then
        assertThat(ended.getStatus()).isEqualTo(LiveStatus.ENDED);
        assertThat(ended.getActualEndAt()).isNotNull();
    }
}
