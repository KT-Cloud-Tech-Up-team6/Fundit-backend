package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.event.LiveEventOutboxJpaEntity;
import com.fundit.live.infrastructure.persistence.event.LiveEventOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * LIVE 시작/종료(요구사항정의서 6.3.4).
 *
 * <p>IVS 호출이 실패하면 상태를 {@code ERROR}로 남기고 사유를 적은 뒤 예외를 던진다 —
 * 그냥 던지고 말면 판매자 화면이 "무슨 일이 있었는지"를 보여줄 수 없다.
 */
@Service
@RequiredArgsConstructor
public class LiveStreamService {

    private final LiveSessionRepository sessionRepository;
    private final LiveEventOutboxJpaRepository outboxRepository;
    private final IvsClient ivsClient;

    @Transactional
    public LiveSession start(UUID sellerId, UUID liveId) {
        LiveSession session = loadOwned(sellerId, liveId);
        Instant now = Instant.now();
        String chatRoomArn;
        try {
            chatRoomArn = ivsClient.createChatRoom(liveId.toString());
        } catch (RuntimeException e) {
            session.markError("채팅방 생성 실패: " + e.getClass().getSimpleName(), now);
            sessionRepository.save(session);
            throw new DependencyFailureException(e);
        }
        session.start(now, chatRoomArn);
        LiveSession saved = sessionRepository.save(session);
        // 도메인 변경과 같은 트랜잭션에 적재한다 — 방송은 시작됐는데 이벤트만 사라지는 경우가 없다.
        appendOutbox(saved, LiveEventOutboxJpaEntity.TYPE_LIVE_STARTED, now);
        return saved;
    }

    @Transactional
    public LiveSession end(UUID sellerId, UUID liveId) {
        LiveSession session = loadOwned(sellerId, liveId);
        Instant now = Instant.now();
        session.end(now);
        LiveSession saved = sessionRepository.save(session);
        // live.ended.v1은 방송 후 자산(질문요약·하이라이트) 두 종류의 유일한 트리거다.
        // 유실되면 방송이 이미 끝나서 재생성할 방법이 없다.
        appendOutbox(saved, LiveEventOutboxJpaEntity.TYPE_LIVE_ENDED, now);
        return saved;
    }

    private void appendOutbox(LiveSession session, String eventType, Instant occurredAt) {
        String payload = """
                {"liveId":"%s","projectId":"%s","occurredAt":"%s"}"""
                .formatted(session.getPublicId(), session.getProjectId(), occurredAt);
        outboxRepository.save(LiveEventOutboxJpaEntity.builder()
                .eventType(eventType)
                .liveSessionId(session.getId())
                .payload(payload)
                .build());
    }

    private LiveSession loadOwned(UUID sellerId, UUID liveId) {
        return sessionRepository.findOwned(liveId, sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
