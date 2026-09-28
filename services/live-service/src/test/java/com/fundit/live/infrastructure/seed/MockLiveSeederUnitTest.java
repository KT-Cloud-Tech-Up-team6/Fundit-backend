package com.fundit.live.infrastructure.seed;

import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MockLiveSeederUnitTest {

    @Mock private LiveChannelJpaRepository channelRepository;
    @Mock private LiveSessionJpaRepository sessionRepository;
    @InjectMocks private MockLiveSeeder seeder;

    @Test
    void 시드_파일의_방송_50건을_읽는다() throws Exception {
        // when
        List<MockLiveSeeder.MockLive> lives = seeder.read();

        // then — PM 기준 홀수 SCHEDULED 25건, 짝수 LIVE 25건. LIVE는 시작 시각이 있어야 경과시간이 계산된다
        assertThat(lives).hasSize(50);
        assertThat(lives).filteredOn(l -> l.session().status() == LiveStatus.LIVE).hasSize(25)
                .allSatisfy(l -> assertThat(l.session().actualStartAt()).isNotNull());
    }

    @Test
    void 채널이_있으면_재사용하고_없는_방송만_만든다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        given(channelRepository.findBySellerId(sellerId)).willReturn(Optional.of(
                LiveChannelJpaEntity.builder().id(7L).sellerId(sellerId).build()));
        given(sessionRepository.findByPublicId(publicId)).willReturn(Optional.empty());

        // when
        int created = seeder.seed(List.of(live(sellerId, publicId)));

        // then
        assertThat(created).isEqualTo(1);
        verify(channelRepository, never()).save(any());
        ArgumentCaptor<LiveSessionJpaEntity> captor = ArgumentCaptor.forClass(LiveSessionJpaEntity.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getChannelId()).isEqualTo(7L);
        assertThat(captor.getValue().getPublicId()).isEqualTo(publicId);
    }

    @Test
    void 방송이_이미_있으면_건너뛴다() {
        // given — 재배포해도 중복 생성되지 않는다
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        given(channelRepository.findBySellerId(sellerId)).willReturn(Optional.of(
                LiveChannelJpaEntity.builder().id(7L).sellerId(sellerId).build()));
        given(sessionRepository.findByPublicId(publicId)).willReturn(Optional.of(
                LiveSessionJpaEntity.builder().build()));

        // when
        int created = seeder.seed(List.of(live(sellerId, publicId)));

        // then
        assertThat(created).isZero();
        verify(sessionRepository, never()).save(any());
    }

    private static MockLiveSeeder.MockLive live(UUID sellerId, UUID publicId) {
        return new MockLiveSeeder.MockLive(sellerId,
                new MockLiveSeeder.MockChannel("arn", "rtmps://ingest", "https://playback"),
                new MockLiveSeeder.MockSession(publicId, UUID.randomUUID(), LiveStatus.LIVE, "가전", "생활가전",
                        "소개", Instant.now(), Instant.now(), 3));
    }
}
