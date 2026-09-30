package com.fundit.live.application.chat;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaEntity;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import com.fundit.live.application.member.MemberNicknameClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 다시보기 시간대별 채팅(요구사항정의서 11.4.4). LIVE 중에도 동작한다 — 방송 중간에 들어온 시청자가
 * 이전 채팅을 채울 때 쓴다(IVS Chat은 지난 메시지를 보내 주지 않는다).
 *
 * <p>입력이 초 단위 <b>구간</b>인 이유: 시점 1개씩 왕복하면 요청 수가 방송 길이만큼 늘어난다.
 * 방송 시작 시각을 기준으로 초를 절대 시각으로 바꿔 조회한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VodChatQueryService {

    /**
     * 한 번에 조회할 수 있는 구간 상한. 방송 길이 상한(요구사항정의서 6.2.3, 10분)과 같은 값이라
     * 한 번에 전체를 읽는 셈이고, 다시보기 UI는 재생 위치를 따라가며 짧은 구간을 반복 조회한다.
     */
    private static final int MAX_RANGE_SEC = 600;

    private final ChatMessageJpaRepository chatMessageRepository;
    private final LiveSessionJpaRepository sessionRepository;
    private final MemberNicknameClient memberNicknameClient;

    public VodChat findByRange(UUID liveId, int fromSec, int toSec) {
        if (fromSec < 0 || toSec < fromSec) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "구간이 올바르지 않습니다.");
        }
        if (toSec - fromSec > MAX_RANGE_SEC) {
            // 상한이 없으면 fromSec=0&toSec=999999999 하나로 방송 전체 채팅을 긁어간다.
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "조회 구간은 %d초 이내여야 합니다.".formatted(MAX_RANGE_SEC));
        }
        LiveSessionJpaEntity session = sessionRepository.findPublicByPublicId(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Instant base = session.getActualStartAt();
        if (base == null) {
            // 시작한 적 없는 방송은 기준 시각이 없어 초를 시각으로 바꿀 수 없다.
            throw new BusinessException(CommonErrorCode.CONFLICT, "송출 기록이 없는 방송입니다.");
        }
        List<ChatMessageJpaEntity> messages = chatMessageRepository
                .findBySessionIdAndSentAtBetweenOrderBySentAtAsc(
                        session.getId(), base.plus(Duration.ofSeconds(fromSec)),
                        base.plus(Duration.ofSeconds(toSec)));
        // 기준 시각을 함께 돌려준다 — 호출부가 이걸 모르면 경과 초를 계산할 수 없다.
        return new VodChat(base, messages);
    }

    /**
     * 화면 표시용 — {@link #findByRange} 결과에 발신자 닉네임을 붙인다. 닉네임은 표시용 부가 정보라
     * 조회가 실패해도 채팅은 그대로 돌려준다(라이브 카드 판매자명·채팅 토큰과 같은 방침).
     *
     * <p>{@link #findByRange}에 합치지 않는 이유: 하이라이트 입력 조립({@code HighlightService})이
     * 구간을 반복 조회하는데 거기엔 닉네임이 필요 없다.
     */
    public VodChatView findForDisplay(UUID liveId, int fromSec, int toSec) {
        VodChat chat = findByRange(liveId, fromSec, toSec);
        return new VodChatView(chat.broadcastStartedAt(), chat.messages(), nicknamesOf(chat.messages()));
    }

    private Map<UUID, String> nicknamesOf(List<ChatMessageJpaEntity> messages) {
        List<UUID> senderIds = messages.stream().map(ChatMessageJpaEntity::getSenderId)
                .filter(Objects::nonNull).distinct().toList();
        if (senderIds.isEmpty()) {
            return Map.of();
        }
        try {
            return memberNicknameClient.findNicknames(senderIds);
        } catch (RuntimeException e) {
            log.warn("채팅 발신자 닉네임 조회 실패, 닉네임 없이 돌려준다. senders={}", senderIds.size(), e);
            return Map.of();
        }
    }

    public record VodChat(Instant broadcastStartedAt, List<ChatMessageJpaEntity> messages) {
    }

    public record VodChatView(Instant broadcastStartedAt, List<ChatMessageJpaEntity> messages,
                              Map<UUID, String> nicknameBySenderId) {
    }
}
