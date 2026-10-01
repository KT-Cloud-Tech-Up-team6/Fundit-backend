package com.fundit.live.application.session;

import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @ParameterizedTest
    @ValueSource(strings = {"Recording Start", "Recording Start Failure", "Recording End Failure"})
    void 녹화_완료가_아니면_저장하지_않는다(String status) {
        // when
        service.recordingEnded(CHANNEL_ARN, status, PREFIX);

        // then
        verify(channelRepository, never()).findByIvsChannelArn(anyString());
        verify(sessionRepository, never()).fillVodIfAbsent(anyLong(), anyString(), any());
    }

    @Test
    void 모르는_채널이면_저장하지_않는다() {
        // given
        given(channelRepository.findByIvsChannelArn(CHANNEL_ARN)).willReturn(Optional.empty());

        // when
        service.recordingEnded(CHANNEL_ARN, "Recording End", PREFIX);

        // then
        verify(sessionRepository, never()).fillVodIfAbsent(anyLong(), anyString(), any());
    }

    @Test
    void 녹화_경로나_채널이_비면_저장하지_않는다() {
        // when
        service.recordingEnded(CHANNEL_ARN, "Recording End", null);
        service.recordingEnded(CHANNEL_ARN, "Recording End", " ");
        service.recordingEnded(null, "Recording End", PREFIX);

        // then
        verify(sessionRepository, never()).fillVodIfAbsent(anyLong(), anyString(), any());
    }
}
