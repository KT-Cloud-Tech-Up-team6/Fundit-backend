package com.fundit.live.application.session;

import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 무시하는 경우는 {@link LiveVodServiceUnitExceptionTest} 참고. */
@ExtendWith(MockitoExtension.class)
class LiveVodServiceUnitTest {

    // 인프라가 준 실제 이벤트 샘플(vod.md, 10-01) 값
    private static final String CHANNEL_ARN = "arn:aws:ivs:ap-northeast-2:899957568205:channel/fundit-dev-channel-1";
    private static final String PREFIX = "ivs/v1/899957568205/fundit-dev-channel-1/2026/10/1/6/0/st-1234567890abcdef";
    private static final String EXPECTED_URL = "https://infrastudy.store/" + PREFIX + "/media/hls/master.m3u8";

    @Mock private LiveChannelJpaRepository channelRepository;
    @Mock private LiveSessionJpaRepository sessionRepository;

    private LiveVodService service;

    @BeforeEach
    void setUp() {
        // 끝 슬래시가 붙은 설정값도 정리돼야 한다
        service = new LiveVodService(channelRepository, sessionRepository, "https://infrastudy.store/");
    }

    private void givenChannel(long id) {
        LiveChannelJpaEntity channel = mock(LiveChannelJpaEntity.class);
        given(channel.getId()).willReturn(id);
        given(channelRepository.findByIvsChannelArn(CHANNEL_ARN)).willReturn(Optional.of(channel));
    }

    @Test
    void 녹화_완료면_CDN_마스터_플레이리스트_주소로_다시보기를_채운다() {
        // given
        givenChannel(7L);
        given(sessionRepository.fillVodIfAbsent(anyLong(), anyString(), any())).willReturn(1);

        // when
        service.recordingEnded(CHANNEL_ARN, "Recording End", PREFIX);

        // then
        verify(sessionRepository).fillVodIfAbsent(eq(7L), eq(EXPECTED_URL), any());
    }

    @Test
    void 경로_앞뒤_슬래시가_겹치지_않는다() {
        // given
        givenChannel(7L);

        // when
        service.recordingEnded(CHANNEL_ARN, "Recording End", "/" + PREFIX + "/");

        // then
        verify(sessionRepository).fillVodIfAbsent(eq(7L), eq(EXPECTED_URL), any());
    }
}
