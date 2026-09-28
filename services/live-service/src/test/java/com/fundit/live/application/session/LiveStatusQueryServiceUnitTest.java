package com.fundit.live.application.session;

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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LiveStatusQueryServiceUnitTest {

    @Mock private LiveSessionJpaRepository sessionRepository;
    @Mock private LiveChannelJpaRepository channelRepository;

    @InjectMocks private LiveStatusQueryService liveStatusQueryService;

    private final UUID projectId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();
    private final UUID sellerId = UUID.randomUUID();

    private LiveSessionJpaEntity session(LiveStatus status) {
        return LiveSessionJpaEntity.builder().id(42L).publicId(liveId).channelId(3L)
                .projectId(projectId).status(status).build();
    }

    private void givenChannel() {
        given(channelRepository.findById(3L)).willReturn(Optional.of(
                LiveChannelJpaEntity.builder().id(3L).sellerId(sellerId).build()));
    }

    @Test
    void 프로젝트가_방송_중이면_세션과_판매자를_돌려준다() {
        // given — order가 주문 생성 시 이 세션 id를 주문에 꼬리표로 단다
        given(sessionRepository.findFirstByProjectIdAndStatus(projectId, LiveStatus.LIVE))
                .willReturn(Optional.of(session(LiveStatus.LIVE)));
        givenChannel();

        // when
        Optional<LiveStatusQueryService.LiveStatus> result = liveStatusQueryService.findActiveByProject(projectId);

        // then
        assertThat(result).hasValueSatisfying(s -> {
            assertThat(s.sessionId()).isEqualTo(42L);
            assertThat(s.liveId()).isEqualTo(liveId);
            assertThat(s.status()).isEqualTo("LIVE");
            assertThat(s.sellerId()).isEqualTo(sellerId);
        });
    }

    @Test
    void 프로젝트가_방송_중이_아니면_비어_있다() {
        // given — 대부분의 주문이 이 경우다. 에러가 아니다.
        given(sessionRepository.findFirstByProjectIdAndStatus(projectId, LiveStatus.LIVE))
                .willReturn(Optional.empty());

        // when
        Optional<LiveStatusQueryService.LiveStatus> result = liveStatusQueryService.findActiveByProject(projectId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void 세션이_있으면_LIVE가_아니어도_실제_상태를_돌려준다() {
        // given — order는 "없음"일 때만 정합성 경고를 남긴다. 종료된 방송을 없음으로 뭉치면 안 된다.
        given(sessionRepository.findById(42L)).willReturn(Optional.of(session(LiveStatus.ENDED)));
        givenChannel();

        // when
        Optional<LiveStatusQueryService.LiveStatus> result = liveStatusQueryService.findBySessionId(42L);

        // then
        assertThat(result).hasValueSatisfying(s -> assertThat(s.status()).isEqualTo("ENDED"));
    }

    @Test
    void 세션이_없으면_비어_있다() {
        // given
        given(sessionRepository.findById(99L)).willReturn(Optional.empty());

        // when
        Optional<LiveStatusQueryService.LiveStatus> result = liveStatusQueryService.findBySessionId(99L);

        // then
        assertThat(result).isEmpty();
    }
}
