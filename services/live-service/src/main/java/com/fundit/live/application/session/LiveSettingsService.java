package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** LIVE 기본 설정 등록/수정(요구사항정의서 6.2.4.1). 부분 업데이트다. */
@Service
@RequiredArgsConstructor
public class LiveSettingsService {

    private final LiveSessionRepository sessionRepository;

    @Transactional
    public LiveSession update(UUID sellerId, UUID liveId, String categoryMajor, String categoryMinor,
                              String introText, String thumbnailUrl, Instant scheduledStartAt) {
        LiveSession session = sessionRepository.findOwned(liveId, sellerId)
                // 타인 소유와 없는 LIVE를 같은 404로 응답한다 — 403이면 "그 방송이 존재한다"를
                // 알려줘서 id를 넣어보며 남의 방송 존재 여부를 캐낼 수 있다(security.md S10).
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        session.updateSettings(categoryMajor, categoryMinor, introText, thumbnailUrl, scheduledStartAt);
        return sessionRepository.save(session);
    }
}
