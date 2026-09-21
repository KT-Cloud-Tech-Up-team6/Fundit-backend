package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.application.project.ProjectContextClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/** LIVE 기본 설정 등록/수정(요구사항정의서 6.2.4.1). 부분 업데이트다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveSettingsService {

    private final LiveSessionRepository sessionRepository;
    private final AiClient aiClient;
    private final ProjectContextClient projectContextClient;

    @Transactional
    public LiveSession update(UUID sellerId, UUID liveId, String categoryMajor, String categoryMinor,
                              String introText, String thumbnailUrl, Instant scheduledStartAt) {
        LiveSession session = sessionRepository.findOwned(liveId, sellerId)
                // 타인 소유와 없는 LIVE를 같은 404로 응답한다 — 403이면 "그 방송이 존재한다"를
                // 알려줘서 id를 넣어보며 남의 방송 존재 여부를 캐낼 수 있다(security.md S10).
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        session.updateSettings(categoryMajor, categoryMinor, introText, thumbnailUrl, scheduledStartAt);
        LiveSession saved = sessionRepository.save(session);
        scheduleContextUpdate(saved);
        return saved;
    }

    /**
     * AI 실시간 컨텍스트 재호출 지점(협의 확정) — 값이 실제로 바뀌는 설정 저장 시점에만
     * 부른다(폴링 없음, YAGNI). 방송 종료 시각·다시보기 제공 여부는 도메인에 아직 값이 없어
     * {@code endAt=null}/{@code vodEnabled=true}로 고정한다(운영 정책 확정 전, 확인 대기).
     */
    private void scheduleContextUpdate(LiveSession session) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    ProjectContextClient.ProjectContext context = projectContextClient
                            .find(session.getProjectId()).orElse(null);
                    AiClient.FundingInfo funding = context == null || context.remainingDays() == null
                            ? new AiClient.FundingInfo(null, context == null || context.achievementRate() == null
                                    ? 0 : context.achievementRate())
                            : new AiClient.FundingInfo(Instant.now().plusSeconds(context.remainingDays() * 86400L),
                                    context.achievementRate() == null ? 0 : context.achievementRate());
                    aiClient.updateContext(session.getPublicId().toString(), new AiClient.ContextUpdate(
                            session.getProjectId().toString(),
                            new AiClient.BroadcastInfo(null, true),
                            funding, java.util.Map.of()));
                } catch (RuntimeException e) {
                    log.warn("AI context 갱신 실패, liveId={}", session.getPublicId(), e);
                }
            }
        });
    }
}
