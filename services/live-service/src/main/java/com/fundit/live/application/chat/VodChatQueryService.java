package com.fundit.live.application.chat;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaEntity;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 다시보기 시간대별 채팅(요구사항정의서 11.4.4).
 *
 * <p>입력이 초 단위 <b>구간</b>인 이유: 시점 1개씩 왕복하면 요청 수가 방송 길이만큼 늘어난다.
 * 방송 시작 시각을 기준으로 초를 절대 시각으로 바꿔 조회한다.
 */
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

    public record VodChat(Instant broadcastStartedAt, List<ChatMessageJpaEntity> messages) {
    }
}
