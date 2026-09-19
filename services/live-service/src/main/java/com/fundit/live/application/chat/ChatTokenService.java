package com.fundit.live.application.chat;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * IVS Chat 접속 토큰 발급(요구사항정의서 6.4.4.1).
 *
 * <p>{@code CreateChatToken}은 백엔드만 호출할 수 있어서 <b>발급 지점이 곧 인가 지점</b>이다.
 * 판매자용·소비자용 엔드포인트를 나누지 않는다 — 경로가 둘이면 클라이언트가 자기 역할을
 * 판단해 골라야 하고, 그 판단이 틀리면 권한이 어긋난다. 여기서 호출자를 보고 정한다.
 */
@Service
@RequiredArgsConstructor
public class ChatTokenService {

    /** 방송 소유자. 자기 방송이라 남의 메시지를 지우고 강퇴할 수 있다. */
    private static final List<String> OWNER_CAPABILITIES =
            List.of("SEND_MESSAGE", "DELETE_MESSAGE", "DISCONNECT_USER");
    /** 그 외 로그인 사용자. 보기 권한은 IVS가 암묵적으로 포함하므로 따로 주지 않는다. */
    private static final List<String> VIEWER_CAPABILITIES = List.of("SEND_MESSAGE");

    private final LiveSessionJpaRepository sessionRepository;
    private final LiveChannelJpaRepository channelRepository;
    private final IvsClient ivsClient;

    @Transactional(readOnly = true)
    public ChatToken issue(UUID userId, UUID liveId) {
        LiveSessionJpaEntity session = sessionRepository.findPublicByPublicId(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (session.getIvsChatRoomArn() == null) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "아직 시작되지 않은 방송입니다.");
        }

        boolean owner = channelRepository.findById(session.getChannelId())
                .map(channel -> channel.getSellerId().equals(userId))
                .orElse(false);
        List<String> capabilities = owner ? OWNER_CAPABILITIES : VIEWER_CAPABILITIES;

        return new ChatToken(
                ivsClient.createChatToken(session.getIvsChatRoomArn(), userId.toString(), capabilities),
                session.getIvsChatRoomArn(),
                capabilities);
    }

    public record ChatToken(String token, String roomArn, List<String> capabilities) {
    }
}
