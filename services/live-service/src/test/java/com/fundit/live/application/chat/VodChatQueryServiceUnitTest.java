package com.fundit.live.application.chat;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.member.MemberNicknameClient;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaEntity;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class VodChatQueryServiceUnitTest {

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
    void 초_구간을_방송_시작_기준_절대시각으로_바꿔_조회한다() {
        // given
        givenSession(STARTED);
        given(chatMessageRepository.findBySessionIdAndSentAtBetweenOrderBySentAtAsc(
                1L, STARTED.plusSeconds(60), STARTED.plusSeconds(120)))
                .willReturn(List.of(ChatMessageJpaEntity.builder().content("안녕").build()));

        // when
        var result = vodChatQueryService.findByRange(liveId, 60, 120);

        // then — 기준 시각을 함께 돌려줘야 호출부가 경과 초를 계산할 수 있다
        assertThat(result.broadcastStartedAt()).isEqualTo(STARTED);
        assertThat(result.messages()).hasSize(1);
    }

    @Nested
    class 화면_표시용_조회 {

        private final UUID alice = UUID.randomUUID();
        private final UUID bob = UUID.randomUUID();

        private void givenMessages(ChatMessageJpaEntity... messages) {
            givenSession(STARTED);
            given(chatMessageRepository.findBySessionIdAndSentAtBetweenOrderBySentAtAsc(any(), any(), any()))
                    .willReturn(List.of(messages));
        }

        private ChatMessageJpaEntity from(UUID senderId) {
            return ChatMessageJpaEntity.builder().senderId(senderId).content("안녕").sentAt(STARTED).build();
        }

        @Test
        void 발신자_닉네임을_한_번에_조회해_붙인다() {
            // given — 같은 사람이 여러 번 보내도 한 번만 묻는다
            givenMessages(from(alice), from(bob), from(alice));
            given(memberNicknameClient.findNicknames(List.of(alice, bob)))
                    .willReturn(Map.of(alice, "앨리스", bob, "밥"));

            // when
            var result = vodChatQueryService.findForDisplay(liveId, 0, 600);

            // then
            assertThat(result.messages()).hasSize(3);
            assertThat(result.nicknameBySenderId()).containsEntry(alice, "앨리스").containsEntry(bob, "밥");
        }

        @Test
        void 닉네임_조회에_실패해도_채팅은_돌려준다() {
            // given — 표시용 부가 정보라 채팅 조회를 막지 않는다
            givenMessages(from(alice));
            given(memberNicknameClient.findNicknames(any()))
                    .willThrow(new DependencyFailureException(new RuntimeException()));

            // when
            var result = vodChatQueryService.findForDisplay(liveId, 0, 600);

            // then
            assertThat(result.messages()).hasSize(1);
            assertThat(result.nicknameBySenderId()).isEmpty();
        }

        @Test
        void 채팅이_없으면_닉네임을_조회하지_않는다() {
            // given
            givenMessages();

            // when
            var result = vodChatQueryService.findForDisplay(liveId, 0, 600);

            // then
            assertThat(result.messages()).isEmpty();
            verifyNoInteractions(memberNicknameClient);
        }
    }
}
