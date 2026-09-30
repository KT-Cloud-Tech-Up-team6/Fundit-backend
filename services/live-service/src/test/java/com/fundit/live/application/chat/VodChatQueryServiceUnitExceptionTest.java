package com.fundit.live.application.chat;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.member.MemberNicknameClient;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaEntity;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class VodChatQueryServiceUnitExceptionTest {

    private static final Instant STARTED = Instant.parse("2026-09-10T11:00:00Z");

    @Mock private ChatMessageJpaRepository chatMessageRepository;
    @Mock private LiveSessionJpaRepository sessionRepository;
    @Mock private MemberNicknameClient memberNicknameClient;

    @InjectMocks private VodChatQueryService vodChatQueryService;

    private final UUID liveId = UUID.randomUUID();

    private void givenSession(Instant startedAt) {
        given(sessionRepository.findPublicByPublicId(liveId)).willReturn(Optional.of(
                LiveSessionJpaEntity.builder().id(1L).publicId(liveId).channelId(1L)
                        .projectId(UUID.randomUUID()).status(LiveStatus.ENDED)
                        .actualStartAt(startedAt).likeCount(0).build()));
    }

    @Test
    void 구간이_뒤집히면_400이다() {
        // when & then — 입력 검증은 서버에서 한다(security.md S2)
        assertThatThrownBy(() -> vodChatQueryService.findByRange(liveId, 120, 60))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> vodChatQueryService.findByRange(liveId, -1, 60))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    void 상한을_넘는_구간은_400이다() {
        // given & when & then — 상한이 없으면 toSec=999999999 하나로 방송 전체 채팅을 긁어간다
        assertThatThrownBy(() -> vodChatQueryService.findByRange(liveId, 0, 601))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    void 송출_기록이_없으면_초를_시각으로_바꿀_수_없어_409다() {
        // given
        givenSession(null);

        // when & then
        assertThatThrownBy(() -> vodChatQueryService.findByRange(liveId, 0, 60))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);
    }

    @Test
    void 닉네임_조회에_실패해도_채팅은_돌려준다() {
        // given — 표시용 부가 정보라 채팅 조회를 막지 않는다
        givenSession(STARTED);
        UUID senderId = UUID.randomUUID();
        given(chatMessageRepository.findBySessionIdAndSentAtBetweenOrderBySentAtAsc(any(), any(), any()))
                .willReturn(List.of(ChatMessageJpaEntity.builder()
                        .senderId(senderId).content("안녕").sentAt(STARTED).build()));
        given(memberNicknameClient.findNicknames(any()))
                .willThrow(new DependencyFailureException(new RuntimeException()));

        // when
        var result = vodChatQueryService.findForDisplay(liveId, 0, 600);

        // then
        assertThat(result.messages()).hasSize(1);
        assertThat(result.nicknameBySenderId()).isEmpty();
    }
}
