package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.domain.session.LiveStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveStreamServiceUnitExceptionTest {

    @Mock private LiveSessionRepository sessionRepository;
    @Mock private IvsClient ivsClient;

    @InjectMocks private LiveStreamService liveStreamService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    @Test
    void 남의_방송이면_404다() {
        // given — 타인 소유와 없는 LIVE를 구분해 응답하면 id를 넣어보며 존재 여부를
        // 캐낼 수 있다(security.md S10). 조회 자체가 소유권에 묶여 있어 결과가 같다.
        given(sessionRepository.findOwned(liveId, sellerId)).willReturn(Optional.empty());

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
        given(sessionRepository.findOwned(liveId, sellerId)).willReturn(Optional.of(session));
        given(ivsClient.createChatRoom(anyString())).willThrow(new IllegalStateException("boom"));

        // when & then — 그냥 던지고 말면 판매자 화면이 무슨 일이 있었는지 못 보여준다
        assertThatThrownBy(() -> liveStreamService.start(sellerId, liveId))
                .isInstanceOf(DependencyFailureException.class);

        ArgumentCaptor<LiveSession> captor = ArgumentCaptor.forClass(LiveSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(LiveStatus.ERROR);
        assertThat(captor.getValue().getErrorDetail()).contains("채팅방 생성 실패");
    }
}
