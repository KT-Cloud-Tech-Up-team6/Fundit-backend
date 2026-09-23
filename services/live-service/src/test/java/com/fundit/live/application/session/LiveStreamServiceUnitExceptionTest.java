package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.event.LiveEventOutboxJpaRepository;
import com.fundit.live.domain.session.LiveStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveStreamServiceUnitExceptionTest {

    @Mock private LiveSessionRepository sessionRepository;
    @Mock private LiveEventOutboxJpaRepository outboxRepository;
    @Mock private IvsClient ivsClient;
    @Mock private LiveChannelJpaRepository channelRepository;

    @InjectMocks private LiveStreamService liveStreamService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    @Test
    void 송출_정보는_남의_방송이면_404다() {
        // given — 스트림 키가 새면 타인이 이 채널로 무단 송출한다. 소유권이 먼저다.
        given(sessionRepository.findOwned(liveId, sellerId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> liveStreamService.streamInfo(sellerId, liveId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
        verify(ivsClient, never()).getStreamKeyValue(anyString());
    }

    @Test
    void 송출_정보는_채널이_없으면_404다() {
        // given
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.create(1L, UUID.randomUUID())));
        given(channelRepository.findBySellerId(sellerId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> liveStreamService.streamInfo(sellerId, liveId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 남의_방송이면_404다() {
        // given — 타인 소유와 없는 LIVE를 구분해 응답하면 id를 넣어보며 존재 여부를
        // 캐낼 수 있다(security.md S10). 조회 자체가 소유권에 묶여 있어 결과가 같다.
        given(sessionRepository.findOwnedForUpdate(liveId, sellerId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> liveStreamService.start(sellerId, liveId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void IVS_실패는_ERROR_상태와_사유를_남기고_전파한다() {
        // given
        LiveSession session = LiveSession.create(1L, UUID.randomUUID());
        given(sessionRepository.findOwnedForUpdate(liveId, sellerId)).willReturn(Optional.of(session));
        given(ivsClient.createChatRoom(anyString())).willThrow(new IllegalStateException("boom"));

        // when & then — 그냥 던지고 말면 판매자 화면이 무슨 일이 있었는지 못 보여준다
        assertThatThrownBy(() -> liveStreamService.start(sellerId, liveId))
                .isInstanceOf(DependencyFailureException.class);

        ArgumentCaptor<LiveSession> captor = ArgumentCaptor.forClass(LiveSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(LiveStatus.ERROR);
        assertThat(captor.getValue().getErrorDetail()).contains("채팅방 생성 실패");
    }

    @Test
    void 종료된_방송은_IVS를_부르기_전에_거부된다() {
        // given — 검증이 IVS 호출 뒤에 있으면 채팅방만 만들어진 채 409가 나 자원이 샌다
        LiveSession ended = LiveSession.create(1L, UUID.randomUUID());
        ended.start(Instant.parse("2026-09-10T11:00:00Z"), "arn:chat");
        ended.end(Instant.parse("2026-09-10T11:10:00Z"));
        given(sessionRepository.findOwnedForUpdate(liveId, sellerId)).willReturn(Optional.of(ended));

        // when & then
        assertThatThrownBy(() -> liveStreamService.start(sellerId, liveId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);

        verify(ivsClient, never()).createChatRoom(anyString());
    }

    @Test
    void 거부돼도_종료_상태가_ERROR로_덮이지_않는다() {
        // given — markError가 상태 검증 없이 불리면 ENDED가 ERROR가 되고
        // LivePlaybackService가 status == ENDED를 보므로 다시보기 조회까지 막힌다
        LiveSession ended = LiveSession.create(1L, UUID.randomUUID());
        ended.start(Instant.parse("2026-09-10T11:00:00Z"), "arn:chat");
        ended.end(Instant.parse("2026-09-10T11:10:00Z"));
        given(sessionRepository.findOwnedForUpdate(liveId, sellerId)).willReturn(Optional.of(ended));

        // when
        assertThatThrownBy(() -> liveStreamService.start(sellerId, liveId))
                .isInstanceOf(BusinessException.class);

        // then
        assertThat(ended.getStatus()).isEqualTo(LiveStatus.ENDED);
        verify(sessionRepository, never()).save(any(LiveSession.class));
    }
}
