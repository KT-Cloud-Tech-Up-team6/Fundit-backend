package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
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
    private final IvsClient ivsClient;

    @Transactional
    public LiveSession start(UUID sellerId, UUID liveId) {
        LiveSession session = loadOwned(sellerId, liveId);
        Instant now = Instant.now();
        try {
            ivsClient.createChatRoom(liveId.toString());
        } catch (RuntimeException e) {
            session.markError("채팅방 생성 실패: " + e.getClass().getSimpleName(), now);
            sessionRepository.save(session);
            throw new DependencyFailureException(e);
        }
        session.start(now);
        return sessionRepository.save(session);
    }

    @Transactional
    public LiveSession end(UUID sellerId, UUID liveId) {
        LiveSession session = loadOwned(sellerId, liveId);
        session.end(Instant.now());
        return sessionRepository.save(session);
    }

    private LiveSession loadOwned(UUID sellerId, UUID liveId) {
        return sessionRepository.findOwned(liveId, sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
