package com.fundit.live.application.session;

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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 저장하지 않고 무시하는 경우. 정상 저장은 {@link LiveVodServiceUnitTest} 참고. */
@ExtendWith(MockitoExtension.class)
class LiveVodServiceUnitExceptionTest {

    // 인프라가 준 실제 이벤트 샘플(vod.md, 10-01) 값
    private static final String CHANNEL_ARN = "arn:aws:ivs:ap-northeast-2:899957568205:channel/fundit-dev-channel-1";
    private static final String PREFIX = "ivs/v1/899957568205/fundit-dev-channel-1/2026/10/1/6/0/st-1234567890abcdef";

    @Mock private LiveChannelJpaRepository channelRepository;
    @Mock private LiveSessionJpaRepository sessionRepository;

    private LiveVodService service;

    @BeforeEach
    void setUp() {
        service = new LiveVodService(channelRepository, sessionRepository, "https://infrastudy.store/");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Recording Start", "Recording Start Failure", "Recording End Failure"})
    void 녹화_완료가_아니면_저장하지_않는다(String status) {
        // when
        service.recordingEnded(CHANNEL_ARN, status, PREFIX);

        // then
        verify(channelRepository, never()).findByIvsChannelArn(anyString());
        verify(sessionRepository, never()).fillVodIfAbsent(anyLong(), anyString(), any(), any());
    }

    @Test
    void 모르는_채널이면_저장하지_않는다() {
        // given
        given(channelRepository.findByIvsChannelArn(CHANNEL_ARN)).willReturn(Optional.empty());

        // when
        service.recordingEnded(CHANNEL_ARN, "Recording End", PREFIX);

        // then
        verify(sessionRepository, never()).fillVodIfAbsent(anyLong(), anyString(), any(), any());
    }

    @Test
    void 녹화_경로나_채널이_비면_저장하지_않는다() {
        // when
        service.recordingEnded(CHANNEL_ARN, "Recording End", null);
        service.recordingEnded(CHANNEL_ARN, "Recording End", " ");
        service.recordingEnded(null, "Recording End", PREFIX);

        // then
        verify(sessionRepository, never()).fillVodIfAbsent(anyLong(), anyString(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ivs/v1/899957568205/fundit-dev-channel-1/st-1234567890abcdef",
            "ivs/v1/899957568205/fundit-dev-channel-1/2026/13/1/6/0/st-1234567890abcdef"})
    void 녹화_시각을_읽을_수_없으면_저장하지_않는다(String prefix) {
        // when (#232) — 시각 없이 붙이면 엉뚱한 방송에 붙을 수 있다
        service.recordingEnded(CHANNEL_ARN, "Recording End", prefix);

        // then
        verify(channelRepository, never()).findByIvsChannelArn(anyString());
        verify(sessionRepository, never()).fillVodIfAbsent(anyLong(), anyString(), any(), any());
    }
}
