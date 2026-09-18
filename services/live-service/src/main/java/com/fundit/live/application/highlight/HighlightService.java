package com.fundit.live.application.highlight;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.highlight.LiveHighlightJpaEntity;
import com.fundit.live.infrastructure.persistence.highlight.LiveHighlightJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * LIVE 하이라이트(요구사항정의서 6.6.4).
 *
 * <p>생성은 AI가 하고 이 서비스는 요청·저장·검토·노출을 맡는다. 자동 생성 결과는
 * <b>{@code is_public=false}로 시작</b>한다 — 기본값을 뒤집으면 검수 전 내용이 그대로 샌다.
 */
@Service
@RequiredArgsConstructor
public class HighlightService {

    /** 방송 1회당 클립은 최대 3개(요구사항정의서 6.6.3). 마커는 제한이 없다. */
    private static final int MAX_CLIPS_PER_LIVE = 3;

    private final LiveHighlightJpaRepository highlightRepository;
    private final LiveSessionRepository sessionRepository;
    private final AiClient aiClient;

    /** 방송 종료 후 자동 생성 요청. 다시보기가 없으면 판별할 영상이 없다. */
    @Transactional
    public void requestGeneration(UUID sellerId, UUID liveId) {
        LiveSession session = loadOwned(sellerId, liveId);
        if (session.getVodUrl() == null) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "다시보기가 저장되지 않아 하이라이트를 만들 수 없습니다.");
        }
        aiClient.requestHighlights(liveId.toString(), session.getVodUrl());
    }

    @Transactional(readOnly = true)
    public List<LiveHighlightJpaEntity> findAll(UUID sellerId, UUID liveId) {
        return highlightRepository.findBySessionIdOrderByStartSecAsc(loadOwned(sellerId, liveId).getId());
    }

    /**
     * 소비자 공개 조회. 여기서 조회 수를 센다 — 소비자 화면이 하이라이트를 보려면
     * 어차피 이 API를 부르므로, 이 호출이 곧 노출이다.
     */
    @Transactional
    public List<LiveHighlightJpaEntity> findPublic(UUID liveId) {
        LiveSession session = sessionRepository.findOwnedAny(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        highlightRepository.increaseViewCount(session.getId());
        return highlightRepository.findBySessionIdAndIsPublicTrueOrderByStartSecAsc(session.getId());
    }

    @Transactional
    public void recordClick(UUID highlightId) {
        highlightRepository.increaseClickCount(highlightId);
    }

    @Transactional
    public LiveHighlightJpaEntity edit(UUID sellerId, UUID liveId, UUID highlightId,
                                       Integer startSec, Integer endSec, String sceneLabel,
                                       String title, String caption) {
        LiveHighlightJpaEntity highlight = loadOwnedHighlight(sellerId, liveId, highlightId);
        highlight.edit(startSec, endSec, sceneLabel, title, caption);
        return highlight;
    }

    @Transactional
    public void regenerate(UUID sellerId, UUID liveId, UUID highlightId) {
        LiveSession session = loadOwned(sellerId, liveId);
        loadOwnedHighlight(sellerId, liveId, highlightId).markRegenerating();
        aiClient.requestHighlights(liveId.toString(), session.getVodUrl());
    }

    @Transactional
    public void delete(UUID sellerId, UUID liveId, UUID highlightId) {
        highlightRepository.delete(loadOwnedHighlight(sellerId, liveId, highlightId));
    }

    /** 공개 설정(요구사항정의서 6.6.4). 생성 실패분은 공개할 수 없다. */
    @Transactional
    public LiveHighlightJpaEntity changeVisibility(UUID sellerId, UUID liveId, UUID highlightId,
                                                   boolean isPublic) {
        LiveHighlightJpaEntity highlight = loadOwnedHighlight(sellerId, liveId, highlightId);
        if (isPublic && !highlight.isPublishable()) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "생성에 실패한 항목은 공개할 수 없습니다.");
        }
        highlight.changeVisibility(isPublic);
        return highlight;
    }

    /**
     * AI가 결과를 밀어주는 경로(내부 전용).
     *
     * <p>클립 개수 상한은 <b>서버에서 검증한다</b> — AI가 더 보내도 초과분은 받지 않는다.
     * 일부 클립만 실패해도 성공분은 정상 저장한다(요구사항정의서 6.6.4).
     */
    @Transactional
    public void applyGenerated(UUID liveId, List<GeneratedHighlight> generated) {
        LiveSession session = sessionRepository.findOwnedAny(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        long existingClips = highlightRepository.countBySessionIdAndKind(
                session.getId(), LiveHighlightJpaEntity.KIND_CLIP);

        for (GeneratedHighlight g : generated) {
            boolean isClip = LiveHighlightJpaEntity.KIND_CLIP.equals(g.kind());
            if (isClip && existingClips >= MAX_CLIPS_PER_LIVE) {
                throw new BusinessException(CommonErrorCode.BUSINESS_RULE_VIOLATION,
                        "방송 1회당 클립은 최대 %d개입니다.".formatted(MAX_CLIPS_PER_LIVE));
            }
            if (isClip) {
                existingClips++;
            }
            highlightRepository.save(LiveHighlightJpaEntity.builder()
                    .publicId(UUID.randomUUID())
                    .sessionId(session.getId())
                    .kind(g.kind())
                    .sceneLabel(g.sceneLabel())
                    .title(g.title())
                    .startSec(g.startSec())
                    .endSec(g.endSec())
                    .clipUrl(g.clipUrl())
                    .caption(g.caption())
                    // 판매자가 확정하기 전까지 소비자 화면에 나오지 않는다(요구사항정의서 6.6.3).
                    .isPublic(false)
                    .generationStatus(g.status())
                    .viewCount(0)
                    .clickCount(0)
                    .build());
        }
    }

    private LiveSession loadOwned(UUID sellerId, UUID liveId) {
        return sessionRepository.findOwned(liveId, sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private LiveHighlightJpaEntity loadOwnedHighlight(UUID sellerId, UUID liveId, UUID highlightId) {
        Long sessionId = loadOwned(sellerId, liveId).getId();
        LiveHighlightJpaEntity highlight = highlightRepository.findByPublicId(highlightId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        // 다른 방송의 하이라이트 id를 넣어 조작하는 걸 막는다(S4).
        if (!highlight.getSessionId().equals(sessionId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return highlight;
    }

    public record GeneratedHighlight(String kind, String sceneLabel, String title, int startSec,
                                     Integer endSec, String clipUrl, String caption, String status) {
    }
}
