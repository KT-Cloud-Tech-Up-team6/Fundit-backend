package com.fundit.live.application.chat;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.application.member.MemberNicknameClient;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IVS Chat 접속 토큰 발급(요구사항정의서 6.4.4.1).
 *
 * <p>{@code CreateChatToken}은 백엔드만 호출할 수 있어서 <b>발급 지점이 곧 인가 지점</b>이다.
 * 판매자용·소비자용 엔드포인트를 나누지 않는다 — 경로가 둘이면 클라이언트가 자기 역할을
 * 판단해 골라야 하고, 그 판단이 틀리면 권한이 어긋난다. 여기서 호출자를 보고 정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatTokenService {

    /** 방송 소유자. 자기 방송이라 남의 메시지를 지우고 강퇴할 수 있다. */
    private static final List<String> OWNER_CAPABILITIES =
            List.of("SEND_MESSAGE", "DELETE_MESSAGE", "DISCONNECT_USER");
    /** 그 외 로그인 사용자. 보기 권한은 IVS가 암묵적으로 포함하므로 따로 주지 않는다. */
    private static final List<String> VIEWER_CAPABILITIES = List.of("SEND_MESSAGE");
    /** 비로그인. 빈 권한 = 보기 전용(IVS가 보기 권한은 항상 준다). */
    private static final List<String> GUEST_CAPABILITIES = List.of();

    static final String GUEST_USER_ID_PREFIX = "guest-";
    static final String NICKNAME_ATTRIBUTE = "nickname";

    private final LiveSessionJpaRepository sessionRepository;
    private final LiveChannelJpaRepository channelRepository;
    private final IvsClient ivsClient;
    private final MemberNicknameClient memberNicknameClient;

    /**
     * @param userId 로그인 사용자. 비로그인이면 null — 보기 전용 토큰을 준다.
     */
    @Transactional(readOnly = true)
    public ChatToken issue(UUID userId, UUID liveId) {
        LiveSessionJpaEntity session = sessionRepository.findPublicByPublicId(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        // 채팅방은 종료 후에도 남아 있어 ARN만 보면 끝난 방송에도 토큰이 나간다 — 상태로 막는다.
        if (session.getStatus() != LiveStatus.LIVE || session.getIvsChatRoomArn() == null) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "진행 중인 방송이 아닙니다.");
        }

        if (userId == null) {
            // ponytail: 비로그인 호출 제한이 없다. IVS CreateChatToken 한도가 문제 되면 게이트웨이 rate limit으로 막는다.
            return issueToken(session, GUEST_USER_ID_PREFIX + UUID.randomUUID(), GUEST_CAPABILITIES, Map.of());
        }

        boolean owner = channelRepository.findById(session.getChannelId())
                .map(channel -> channel.getSellerId().equals(userId))
                .orElse(false);
        return issueToken(session, userId.toString(),
                owner ? OWNER_CAPABILITIES : VIEWER_CAPABILITIES, nicknameAttributes(userId));
    }

    private ChatToken issueToken(LiveSessionJpaEntity session, String ivsUserId, List<String> capabilities,
                                 Map<String, String> attributes) {
        return new ChatToken(
                ivsClient.createChatToken(session.getIvsChatRoomArn(), ivsUserId, capabilities, attributes),
                session.getIvsChatRoomArn(),
                capabilities);
    }

    /**
     * 닉네임은 채팅 표시용 부가 정보라 조회가 실패해도 접속은 막지 않는다 — 속성 없이 발급하면
     * FE가 기본 표시("시청자")로 보여준다. 라이브 카드 판매자명({@code LiveQueryService})과 같은 방침.
     */
    private Map<String, String> nicknameAttributes(UUID userId) {
        try {
            String nickname = memberNicknameClient.findNicknames(List.of(userId)).get(userId);
            return nickname == null ? Map.of() : Map.of(NICKNAME_ATTRIBUTE, nickname);
        } catch (RuntimeException e) {
            log.warn("채팅 닉네임 조회 실패, 닉네임 없이 발급한다. userId={}", userId, e);
            return Map.of();
        }
    }

    public record ChatToken(String token, String roomArn, List<String> capabilities) {
    }
}
