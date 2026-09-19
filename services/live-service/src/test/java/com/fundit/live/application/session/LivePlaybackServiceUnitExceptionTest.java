package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LivePlaybackServiceUnitExceptionTest {

    @Mock private LiveSessionJpaRepository sessionRepository;
    @Mock private LiveChannelJpaRepository channelRepository;

    @InjectMocks private LivePlaybackService livePlaybackService;

    private final UUID liveId = UUID.randomUUID();

    private LiveSessionJpaEntity.LiveSessionJpaEntityBuilder session(LiveStatus status) {
        return LiveSessionJpaEntity.builder().publicId(liveId).channelId(1L)
                .projectId(UUID.randomUUID()).status(status).likeCount(0);
    }

    @Test
    void DRAFT는_존재_자체를_알리지_않는다() {
        // given — 공개 로더가 DRAFT를 쿼리에서 걸러 빈 Optional이 온다.
        // 호출부마다 if로 거르면 다른 경로에서 빠뜨린다(security.md S10)
        given(sessionRepository.findPublicByPublicId(liveId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> livePlaybackService.playback(liveId)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 인코딩_전_다시보기는_409다() {
        // given — 404로 뭉개면 "없는 방송"과 구분되지 않는다
        given(sessionRepository.findPublicByPublicId(liveId)).willReturn(Optional.of(session(LiveStatus.ENDED).build()));

        // when & then
        assertThatThrownBy(() -> livePlaybackService.vod(liveId)).isInstanceOf(BusinessException.class);
    }
}
