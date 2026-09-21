package com.fundit.live.application.cuesheet;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.application.ai.AiProductContextAssembler;
import com.fundit.live.domain.ai.GenerationStatus;
import com.fundit.live.domain.cuesheet.LiveCueSheet;
import com.fundit.live.domain.cuesheet.LiveCueSheetRepository;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * AI 큐시트 생성/조회/수정(요구사항정의서 6.2.4.2).
 *
 * <p>{@code jobId}를 따로 발급하지 않는다 — 세션당 큐시트가 1개라 {@code GET}의 status로
 * 폴링하면 충분하고, 별도 식별자는 조회 경로가 없어 쓸 데가 없다.
 */
@Service
@RequiredArgsConstructor
public class CueSheetService {

    /** 요구사항정의서 6.2.3: AI 큐시트는 방송 길이 10분 이내로 구성한다. */
    private static final int MAX_DURATION_SEC = 600;

    private final LiveCueSheetRepository cueSheetRepository;
    private final LiveSessionRepository sessionRepository;
    private final AiClient aiClient;
    private final AiProductContextAssembler productContextAssembler;

    @Transactional
    public void requestGeneration(UUID sellerId, UUID liveId, String mode, int targetDurationSec,
                                  boolean demoAvailable, List<String> emphasisPoints, String tone,
                                  List<String> mandatoryPhrases) {
        if (targetDurationSec <= 0 || targetDurationSec > MAX_DURATION_SEC) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "방송 길이는 10분 이내여야 합니다.");
        }
        // 행을 잠근다 — 잠그지 않으면 더블클릭한 두 요청이 둘 다 "생성 중 아님"을 보고
        // AI 작업이 두 번 돈다. 나중 결과가 먼저 것을 덮어써 어느 쪽이 남는지 알 수 없다.
        // 조회(find)·수정(replaceSegments)은 잠그지 않는다 — 조회에 쓰기 잠금은 걸지 않는다.
        LiveSession session = sessionRepository.findOwnedForUpdate(liveId, sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        cueSheetRepository.findBySessionId(session.getId()).ifPresent(existing -> {
            // 생성 중 중복 요청을 막는다. 두 번 돌면 결과가 서로 덮어써 어느 쪽이 남는지 알 수 없다.
            if (existing.isGenerating()) {
                throw new BusinessException(CommonErrorCode.CONFLICT, "큐시트를 생성하고 있습니다.");
            }
        });

        // 상품정보를 GENERATING 저장보다 먼저 모은다 — project-service 장애면 503으로 끝나고
        // 트랜잭션이 롤백돼 GENERATING에 갇히지 않는다. 상품 내용 없이는 AI가 대사를 못 쓴다.
        AiClient.PrepareRequest product = productContextAssembler.assemble(session);

        // 재생성은 기존 행을 덮어쓴다 — 이력 보관 요구가 없다.
        cueSheetRepository.save(LiveCueSheet.requestGeneration(session.getId(), mode, targetDurationSec));

        aiClient.requestCueSheet(liveId.toString(), new AiClient.CueSheetRequest(
                mode, targetDurationSec, demoAvailable, emphasisPoints, tone, mandatoryPhrases, product));
    }

    @Transactional(readOnly = true)
    public LiveCueSheet find(UUID sellerId, UUID liveId) {
        return load(loadOwned(sellerId, liveId).getId());
    }

    /** 판매자 직접 수정. 구간 추가·순서 변경도 이 경로다. */
    @Transactional
    public LiveCueSheet replaceSegments(UUID sellerId, UUID liveId, String segmentsJson) {
        LiveCueSheet cueSheet = load(loadOwned(sellerId, liveId).getId());
        cueSheet.replaceSegments(segmentsJson);
        return cueSheetRepository.save(cueSheet);
    }

    /**
     * AI가 결과를 밀어주는 경로(내부 전용). 구조·길이를 검증한 뒤 저장한다 —
     * 외부 응답을 그대로 신뢰하지 않는다(security.md S7).
     *
     * <p>모르는 status를 FAILED로 굳히지 않는다 — AI가 {@code "PROCESSING"}을 보내면
     * 실패로 저장되고 되돌릴 경로가 없다. 아는 값 둘만 받고 나머지는 400이다.
     */
    @Transactional
    public void applyResult(UUID liveId, String status, String segmentsJson, String failureReason) {
        LiveSession session = sessionRepository.findOwnedAny(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        LiveCueSheet cueSheet = load(session.getId());

        if (GenerationStatus.COMPLETED.name().equals(status)) {
            cueSheet.complete(segmentsJson);
        } else if (GenerationStatus.FAILED.name().equals(status)) {
            cueSheet.fail(failureReason);
        } else {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "알 수 없는 생성 상태입니다: %s".formatted(status));
        }
        cueSheetRepository.save(cueSheet);
    }

    private LiveCueSheet load(Long sessionId) {
        return cueSheetRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private LiveSession loadOwned(UUID sellerId, UUID liveId) {
        return sessionRepository.findOwned(liveId, sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
