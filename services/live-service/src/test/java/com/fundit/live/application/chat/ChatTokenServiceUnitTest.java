package com.fundit.live.application.chat;

import com.fundit.common.error.BusinessException;
import com.fundit.live.application.ivs.IvsClient;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChatTokenServiceUnitTest {

    @Mock private LiveSessionJpaRepository sessionRepository;
    @Mock private LiveChannelJpaRepository channelRepository;
    @Mock private IvsClient ivsClient;

    @InjectMocks private ChatTokenService chatTokenService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    private void givenLiveSession() {
        given(sessionRepository.findPublicByPublicId(liveId)).willReturn(Optional.of(
                LiveSessionJpaEntity.builder().publicId(liveId).channelId(1L)
                        .projectId(UUID.randomUUID()).status(LiveStatus.LIVE)
                        .ivsChatRoomArn("arn:room").likeCount(0).build()));
        given(ivsClient.createChatToken(anyString(), anyString(), any())).willReturn("token");
    }

    @Test
    void 방송_소유자는_삭제_강퇴_권한까지_받는다() {
        // given
        givenLiveSession();
        given(channelRepository.findById(1L)).willReturn(Optional.of(
                LiveChannelJpaEntity.builder().id(1L).sellerId(sellerId).build()));

        // when
        ChatTokenService.ChatToken token = chatTokenService.issue(sellerId, liveId);

        // then
        assertThat(token.capabilities())
                .containsExactly("SEND_MESSAGE", "DELETE_MESSAGE", "DISCONNECT_USER");
    }

    @Test
    void 일반_시청자는_전송_권한만_받는다() {
        // given — 경로가 하나라 클라이언트가 자기 역할을 판단할 필요가 없다
        givenLiveSession();
        given(channelRepository.findById(1L)).willReturn(Optional.of(
                LiveChannelJpaEntity.builder().id(1L).sellerId(sellerId).build()));

        // when
        ChatTokenService.ChatToken token = chatTokenService.issue(UUID.randomUUID(), liveId);

        // then — 보기 권한은 IVS가 암묵적으로 포함하므로 따로 주지 않는다
        assertThat(token.capabilities()).containsExactly("SEND_MESSAGE");
    }


}
