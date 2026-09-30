package com.fundit.live.application.chat;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.application.member.MemberNicknameClient;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatTokenServiceUnitTest {

    @Mock private LiveSessionJpaRepository sessionRepository;
    @Mock private LiveChannelJpaRepository channelRepository;
    @Mock private IvsClient ivsClient;
    @Mock private MemberNicknameClient memberNicknameClient;

    @InjectMocks private ChatTokenService chatTokenService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID viewerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    private void givenLiveSession() {
        given(sessionRepository.findPublicByPublicId(liveId)).willReturn(Optional.of(
                LiveSessionJpaEntity.builder().publicId(liveId).channelId(1L)
                        .projectId(UUID.randomUUID()).status(LiveStatus.LIVE)
                        .ivsChatRoomArn("arn:room").likeCount(0).build()));
        given(ivsClient.createChatToken(anyString(), anyString(), anyList(), anyMap())).willReturn("token");
    }

    private void givenChannelOwnedBySeller() {
        given(channelRepository.findById(1L)).willReturn(Optional.of(
                LiveChannelJpaEntity.builder().id(1L).sellerId(sellerId).build()));
    }

    @Nested
    class 권한 {

        @Test
        void 방송_소유자는_삭제_강퇴_권한까지_받는다() {
            // given
            givenLiveSession();
            givenChannelOwnedBySeller();

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
            givenChannelOwnedBySeller();

            // when
            ChatTokenService.ChatToken token = chatTokenService.issue(viewerId, liveId);

            // then — 보기 권한은 IVS가 암묵적으로 포함하므로 따로 주지 않는다
            assertThat(token.capabilities()).containsExactly("SEND_MESSAGE");
        }

        @Test
        void 비로그인은_보기_전용이고_회원_ID를_쓰지_않는다() {
            // given
            givenLiveSession();

            // when
            ChatTokenService.ChatToken token = chatTokenService.issue(null, liveId);

            // then — 빈 권한 = 보기 전용. 닉네임도 조회하지 않는다
            assertThat(token.capabilities()).isEmpty();
            verify(ivsClient).createChatToken(eq("arn:room"),
                    startsWith(ChatTokenService.GUEST_USER_ID_PREFIX), eq(List.of()), eq(Map.of()));
            verifyNoInteractions(memberNicknameClient, channelRepository);
        }
    }

    @Nested
    class 닉네임 {

        @Test
        void 서버가_조회한_닉네임을_토큰_속성에_싣는다() {
            // given
            givenLiveSession();
            givenChannelOwnedBySeller();
            given(memberNicknameClient.findNicknames(List.of(viewerId))).willReturn(Map.of(viewerId, "펀딧러"));

            // when
            chatTokenService.issue(viewerId, liveId);

            // then
            verify(ivsClient).createChatToken("arn:room", viewerId.toString(), List.of("SEND_MESSAGE"),
                    Map.of(ChatTokenService.NICKNAME_ATTRIBUTE, "펀딧러"));
        }

        @Test
        void 조회에_실패해도_닉네임_없이_발급한다() {
            // given — 표시용 부가 정보라 채팅 접속을 막지 않는다
            givenLiveSession();
            givenChannelOwnedBySeller();
            given(memberNicknameClient.findNicknames(any())).willThrow(new DependencyFailureException(new RuntimeException()));

            // when
            ChatTokenService.ChatToken token = chatTokenService.issue(viewerId, liveId);

            // then
            assertThat(token.token()).isEqualTo("token");
            verify(ivsClient).createChatToken("arn:room", viewerId.toString(), List.of("SEND_MESSAGE"), Map.of());
        }

        @Test
        void 탈퇴한_회원이면_닉네임_없이_발급한다() {
            // given — 없는 회원은 결과에서 빠진다
            givenLiveSession();
            givenChannelOwnedBySeller();
            given(memberNicknameClient.findNicknames(List.of(viewerId))).willReturn(Map.of());

            // when
            chatTokenService.issue(viewerId, liveId);

            // then
            verify(ivsClient).createChatToken("arn:room", viewerId.toString(), List.of("SEND_MESSAGE"), Map.of());
        }
    }
}
