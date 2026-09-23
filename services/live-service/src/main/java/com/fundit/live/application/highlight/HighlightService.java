package com.fundit.live.application.highlight;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.application.chat.VodChatQueryService;
import com.fundit.live.application.project.ProjectContextClient;
import com.fundit.live.domain.ai.GenerationStatus;
import com.fundit.live.domain.highlight.HighlightKind;
import com.fundit.live.domain.highlight.LiveHighlight;
import com.fundit.live.domain.highlight.LiveHighlightRepository;
import com.fundit.live.domain.highlight.SceneLabel;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LIVE 하이라이트(요구사항정의서 6.6.4).
 *
 * <p>생성은 AI가 하고 이 서비스는 요청·저장·검토·노출을 맡는다. 자동 생성 결과는
 * <b>{@code is_public=false}로 시작</b>한다 — 기본값을 뒤집으면 검수 전 내용이 그대로 샌다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HighlightService {

    /** 방송 1회당 클립은 최대 3개(요구사항정의서 6.6.3). 마커는 제한이 없다. */
    private static final int MAX_CLIPS_PER_LIVE = 3;
    /** {@code VodChatQueryService.MAX_RANGE_SEC}와 같은 값 — 그 상한을 넘기면 예외가 난다. */
    private static final int CHAT_QUERY_RANGE_SEC = 600;

    private final LiveHighlightRepository highlightRepository;
    private final LiveSessionRepository sessionRepository;
    private final AiClient aiClient;
    private final ProjectContextClient projectContextClient;
    private final VodChatQueryService vodChatQueryService;

    /** 방송 종료 후 자동 생성 요청. 다시보기가 없으면 판별할 영상이 없다. */
    @Transactional
    public void requestGeneration(UUID sellerId, UUID liveId) {
        LiveSession session = loadOwnedWithVod(sellerId, liveId);
        aiClient.requestHighlights(liveId.toString(), session.getVodUrl(), null,
                chatsOf(session), productNameOf(session));
    }

    @Transactional(readOnly = true)
    public List<LiveHighlight> findAll(UUID sellerId, UUID liveId) {
        return highlightRepository.findAllBySessionId(loadOwned(sellerId, liveId).getId());
    }

    /**
     * 소비자 공개 조회. 여기서 조회 수를 센다 — 소비자 화면이 하이라이트를 보려면
     * 어차피 이 API를 부르므로, 이 호출이 곧 노출이다.
     */
    @Transactional
    public List<LiveHighlight> findPublic(UUID liveId) {
        LiveSession session = loadPublic(liveId);
        highlightRepository.increaseViewCount(session.getId());
        return highlightRepository.findPublicBySessionId(session.getId());
    }

    /** 소속을 확인하고 증가시킨다 — 남의 하이라이트 id로 카운터를 올릴 수 없어야 한다(S4). */
    @Transactional
    public void recordClick(UUID liveId, UUID highlightId) {
        requireBelongsTo(loadPublic(liveId).getId(), highlightId);
        highlightRepository.increaseClickCount(highlightId);
    }

    @Transactional
    public LiveHighlight edit(UUID sellerId, UUID liveId, UUID highlightId,
                              Integer startSec, Integer endSec, SceneLabel sceneLabel,
                              String title, String caption) {
        LiveHighlight highlight = loadOwnedHighlight(sellerId, liveId, highlightId);
        highlight.edit(startSec, endSec, sceneLabel, title, caption);
        return highlightRepository.save(highlight);
    }

    @Transactional
    public void regenerate(UUID sellerId, UUID liveId, UUID highlightId) {
        // 재생성도 같은 영상이 필요하다. 가드를 호출부마다 붙이면 세 번째 호출부에서 또 빠진다.
        LiveSession session = loadOwnedWithVod(sellerId, liveId);
        LiveHighlight highlight = loadOwnedHighlight(sellerId, liveId, highlightId);
        highlight.markRegenerating();
        highlightRepository.save(highlight);
        // 대상 id를 같이 넘긴다 — 결과가 새 행으로 들어오면 원래 행이 GENERATING으로 영영 남고
        // 클립 수가 늘어 상한에 걸린다(재생성 자체가 막힌다).
        aiClient.requestHighlights(liveId.toString(), session.getVodUrl(), highlightId,
                chatsOf(session), productNameOf(session));
    }

    @Transactional
    public void delete(UUID sellerId, UUID liveId, UUID highlightId) {
        highlightRepository.deleteByPublicId(
                loadOwnedHighlight(sellerId, liveId, highlightId).getPublicId());
    }

    /** 공개 설정(요구사항정의서 6.6.4). 생성 실패분을 막는 건 도메인이 한다. */
    @Transactional
    public LiveHighlight changeVisibility(UUID sellerId, UUID liveId, UUID highlightId,
                                          boolean isPublic) {
        LiveHighlight highlight = loadOwnedHighlight(sellerId, liveId, highlightId);
        highlight.changeVisibility(isPublic);
        return highlightRepository.save(highlight);
    }

    /**
     * AI가 결과를 밀어주는 경로(내부 전용).
     *
     * <p>클립 개수 상한은 <b>서버에서 검증한다</b> — AI가 더 보내도 초과분은 받지 않는다.
     * 다만 초과분에 <b>예외를 던지지 않고 건너뛴다</b>: 던지면 {@code @Transactional}이
     * 앞서 저장한 성공분까지 롤백해 "일부만 실패해도 성공분은 정상 노출한다"
     * (요구사항정의서 6.6.4)를 정면으로 어긴다. AI가 더 보낸 건 우리 잘못이 아니고
     * 전부 버리는 쪽이 더 나쁘다.
     *
     * <p>{@code highlightId}가 실려 오면 재생성 결과이므로 <b>기존 행을 갱신</b>한다.
     */
    @Transactional
    public void applyGenerated(UUID liveId, List<GeneratedHighlight> generated) {
        // 행을 잠근다 — 콜백은 at-least-once라 같은 결과가 두 번 오면 둘 다 countClips를
        // 0으로 읽고 각각 3개를 넣는다. 상한이 동시성으로 샌다.
        LiveSession session = sessionRepository.findOwnedAnyForUpdate(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        long existingClips = highlightRepository.countClips(session.getId());

        int skipped = 0;
        for (GeneratedHighlight g : generated) {
            if (g.highlightId() != null) {
                // 이미 자리를 차지하고 있던 행이라 개수가 늘지 않는다.
                LiveHighlight target = requireBelongsTo(session.getId(), g.highlightId());
                target.applyRegenerated(g.sceneLabel(), g.title(), g.startSec(), g.endSec(),
                        g.clipUrl(), g.caption(), g.status());
                highlightRepository.save(target);
                continue;
            }
            if (g.kind() == HighlightKind.CLIP && existingClips >= MAX_CLIPS_PER_LIVE) {
                skipped++;
                continue;
            }
            if (g.kind() == HighlightKind.CLIP) {
                existingClips++;
            }
            highlightRepository.save(LiveHighlight.generated(session.getId(), g.kind(), g.sceneLabel(),
                    g.title(), g.startSec(), g.endSec(), g.clipUrl(), g.caption(), g.status()));
        }
        if (skipped > 0) {
            log.warn("클립 상한({})을 넘겨 {}건을 건너뛰었다. liveId={}", MAX_CLIPS_PER_LIVE, skipped, liveId);
        }
    }

    /**
     * 질문 집중 구간·채팅 활발 구간 판별용(AI팀 요청). 시작·종료 시각이 둘 다 있어야 구간을
     * 계산할 수 있다 — 방송 길이는 백엔드가 강제하지 않아({@code LiveSession}에 상한 없음)
     * 10분을 넘는 방송이 흔하다. {@code VodChatQueryService.MAX_RANGE_SEC}(600초)를 한 번에
     * 넘기면 예외가 나므로 600초씩 나눠 반복 조회하고, 구간 경계에 걸친 메시지는 id로 중복
     * 제거한다.
     */
    private List<AiClient.CommentInput> chatsOf(LiveSession session) {
        if (session.getActualStartAt() == null || session.getActualEndAt() == null) {
            return List.of();
        }
        int endSec = (int) Duration.between(session.getActualStartAt(), session.getActualEndAt()).getSeconds();
        Map<Long, AiClient.CommentInput> byId = new LinkedHashMap<>();
        for (int fromSec = 0; fromSec <= endSec; fromSec += CHAT_QUERY_RANGE_SEC) {
            int toSec = Math.min(fromSec + CHAT_QUERY_RANGE_SEC, endSec);
            vodChatQueryService.findByRange(session.getPublicId(), fromSec, toSec).messages().forEach(m ->
                    byId.putIfAbsent(m.getId(), new AiClient.CommentInput(m.getId().toString(), m.getContent(),
                            Duration.between(session.getActualStartAt(), m.getSentAt()).toMillis(), m.getSenderId())));
            if (toSec == endSec) {
                break;
            }
        }
        return List.copyOf(byId.values());
    }

    /** 쇼츠 제목에 붙일 상품명(AI팀 요청) — 모델이 상품명을 지어내면 틀린 이름이 박히니 우리가 채운다. */
    private String productNameOf(LiveSession session) {
        return projectContextClient.find(session.getProjectId())
                .map(ProjectContextClient.ProjectContext::title)
                .orElse(null);
    }

    /** AI에 영상을 넘기는 경로가 전부 지나는 지점. 다시보기가 없으면 판별할 대상이 없다. */
    private LiveSession loadOwnedWithVod(UUID sellerId, UUID liveId) {
        LiveSession session = loadOwned(sellerId, liveId);
        if (session.getVodUrl() == null) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "다시보기가 저장되지 않아 하이라이트를 만들 수 없습니다.");
        }
        return session;
    }

    private LiveSession loadOwned(UUID sellerId, UUID liveId) {
        return sessionRepository.findOwned(liveId, sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /** 소비자 공개 경로 전용 — DRAFT는 404다. 내부 콜백은 {@code findOwnedAnyForUpdate}를 따로 쓴다. */
    private LiveSession loadPublic(UUID liveId) {
        return sessionRepository.findPublic(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private LiveHighlight loadOwnedHighlight(UUID sellerId, UUID liveId, UUID highlightId) {
        return requireBelongsTo(loadOwned(sellerId, liveId).getId(), highlightId);
    }

    /** 다른 방송의 하이라이트 id를 넣어 조작하는 걸 막는다. 존재를 알리지 않으려 404다(S4·S10). */
    private LiveHighlight requireBelongsTo(Long sessionId, UUID highlightId) {
        LiveHighlight highlight = highlightRepository.findByPublicId(highlightId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!highlight.getSessionId().equals(sessionId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return highlight;
    }

    /** {@code highlightId}는 재생성 대상이며 최초 생성은 null이다. */
    public record GeneratedHighlight(UUID highlightId, HighlightKind kind, SceneLabel sceneLabel,
                                     String title, int startSec, Integer endSec, String clipUrl,
                                     String caption, GenerationStatus status) {
    }
}
