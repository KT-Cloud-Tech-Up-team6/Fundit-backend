package com.fundit.live.application.chat;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.application.member.MemberNicknameClient;
import com.fundit.live.domain.session.LiveStatus;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatTokenServiceUnitExceptionTest {

    @Mock private LiveSessionJpaRepository sessionRepository;
    @Mock private LiveChannelJpaRepository channelRepository;
    @Mock private IvsClient ivsClient;
    @Mock private MemberNicknameClient memberNicknameClient;

    @InjectMocks private ChatTokenService chatTokenService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    @Test
    void 시작_전_방송은_채팅방이_없어_409다() {
        // given
        given(sessionRepository.findPublicByPublicId(liveId)).willReturn(Optional.of(
                LiveSessionJpaEntity.builder().publicId(liveId).channelId(1L)
                        .projectId(UUID.randomUUID()).status(LiveStatus.SCHEDULED).likeCount(0).build()));

        // when & then
        assertThatThrownBy(() -> chatTokenService.issue(sellerId, liveId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);
    }

    @Test
    void 종료된_방송은_채팅방이_남아_있어도_409다() {
        // given — 채팅방은 종료 후에도 남아 있어 ARN만 보면 끝난 방송에 토큰이 나간다
        given(sessionRepository.findPublicByPublicId(liveId)).willReturn(Optional.of(
                LiveSessionJpaEntity.builder().publicId(liveId).channelId(1L)
                        .projectId(UUID.randomUUID()).status(LiveStatus.ENDED)
                        .ivsChatRoomArn("arn:room").likeCount(0).build()));

        // when & then
        assertThatThrownBy(() -> chatTokenService.issue(sellerId, liveId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);
        verifyNoInteractions(ivsClient);
    }

    @Test
    void 비로그인도_종료된_방송은_409다() {
        // given
        given(sessionRepository.findPublicByPublicId(liveId)).willReturn(Optional.of(
                LiveSessionJpaEntity.builder().publicId(liveId).channelId(1L)
                        .projectId(UUID.randomUUID()).status(LiveStatus.ENDED)
                        .ivsChatRoomArn("arn:room").likeCount(0).build()));

        // when & then
        assertThatThrownBy(() -> chatTokenService.issue(null, liveId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);
    }

    @Test
    void 설정_중인_방송은_존재_자체를_알리지_않는다() {
        // given — 공개 로더가 DRAFT를 걸러 빈 Optional이 온다.
        // 409로 답하면 liveId를 넣어보며 존재 여부를 캐낼 수 있다(security.md S10)
        given(sessionRepository.findPublicByPublicId(liveId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatTokenService.issue(sellerId, liveId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((com.fundit.common.error.BusinessException) e).getErrorCode())
                .isEqualTo(com.fundit.common.error.CommonErrorCode.NOT_FOUND);
    }
}
