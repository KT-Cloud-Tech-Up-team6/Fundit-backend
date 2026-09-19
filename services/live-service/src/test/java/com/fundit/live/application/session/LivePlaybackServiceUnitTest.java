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
class LivePlaybackServiceUnitTest {

    @Mock private LiveSessionJpaRepository sessionRepository;
    @Mock private LiveChannelJpaRepository channelRepository;

    @InjectMocks private LivePlaybackService livePlaybackService;

    private final UUID liveId = UUID.randomUUID();

    private LiveSessionJpaEntity.LiveSessionJpaEntityBuilder session(LiveStatus status) {
        return LiveSessionJpaEntity.builder().publicId(liveId).channelId(1L)
                .projectId(UUID.randomUUID()).status(status).likeCount(0);
    }

    @Test
    void 진행중이면_채널_재생URL을_돌려준다() {
        // given
        given(sessionRepository.findPublicByPublicId(liveId)).willReturn(Optional.of(session(LiveStatus.LIVE).build()));
        given(channelRepository.findById(1L)).willReturn(Optional.of(
                LiveChannelJpaEntity.builder().id(1L).ivsPlaybackUrl("https://play/master.m3u8").build()));

        // when
        var playback = livePlaybackService.playback(liveId);

        // then
        assertThat(playback.type()).isEqualTo("LIVE");
        assertThat(playback.playbackUrl()).isEqualTo("https://play/master.m3u8");
    }

    @Test
    void 종료된_방송은_다시보기로_자동_전환된다() {
        // given — 404를 받고 VOD를 따로 재요청하지 않아도 된다(PRD 11.2.4)
        given(sessionRepository.findPublicByPublicId(liveId)).willReturn(Optional.of(
                session(LiveStatus.ENDED).vodUrl("https://vod/1.m3u8")
                        .vodReadyAt(Instant.parse("2026-09-10T12:00:00Z")).build()));

        // when
        var playback = livePlaybackService.playback(liveId);

        // then
        assertThat(playback.type()).isEqualTo("VOD");
        assertThat(playback.playbackUrl()).isEqualTo("https://vod/1.m3u8");
    }


}
