package com.fundit.live.application.cuesheet;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.cuesheet.LiveCueSheetJpaEntity;
import com.fundit.live.infrastructure.persistence.cuesheet.LiveCueSheetJpaRepository;
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

    private final LiveCueSheetJpaRepository cueSheetRepository;
    private final LiveSessionRepository sessionRepository;
    private final AiClient aiClient;

    @Transactional
    public void requestGeneration(UUID sellerId, UUID liveId, String mode, int targetDurationSec,
                                  boolean demoAvailable, List<String> emphasisPoints, String tone,
                                  List<String> mandatoryPhrases) {
        if (targetDurationSec <= 0 || targetDurationSec > MAX_DURATION_SEC) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "방송 길이는 10분 이내여야 합니다.");
        }
        LiveSession session = loadOwned(sellerId, liveId);

        cueSheetRepository.findById(session.getId()).ifPresent(existing -> {
            // 생성 중 중복 요청을 막는다. 두 번 돌면 결과가 서로 덮어써 어느 쪽이 남는지 알 수 없다.
            if (existing.isGenerating()) {
                throw new BusinessException(CommonErrorCode.CONFLICT, "큐시트를 생성하고 있습니다.");
            }
        });

        // 재생성은 기존 행을 덮어쓴다 — 이력 보관 요구가 없다.
        cueSheetRepository.save(LiveCueSheetJpaEntity.builder()
                .sessionId(session.getId())
                .mode(mode)
                .status(LiveCueSheetJpaEntity.STATUS_GENERATING)
                .targetDurationSec(targetDurationSec)
                .build());

        aiClient.requestCueSheet(liveId.toString(), new AiClient.CueSheetRequest(
                mode, targetDurationSec, demoAvailable, emphasisPoints, tone, mandatoryPhrases));
    }

    @Transactional(readOnly = true)
    public LiveCueSheetJpaEntity find(UUID sellerId, UUID liveId) {
        return cueSheetRepository.findById(loadOwned(sellerId, liveId).getId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /** 판매자 직접 수정. 구간 추가·순서 변경도 이 경로다. */
    @Transactional
    public LiveCueSheetJpaEntity replaceSegments(UUID sellerId, UUID liveId, String segmentsJson) {
        LiveCueSheetJpaEntity cueSheet = cueSheetRepository.findById(loadOwned(sellerId, liveId).getId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (cueSheet.isGenerating()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "생성이 끝난 뒤에 수정할 수 있습니다.");
        }
        cueSheet.replaceSegments(segmentsJson);
        return cueSheet;
    }

    /**
     * AI가 결과를 밀어주는 경로(내부 전용). 구조·길이를 검증한 뒤 저장한다 —
     * 외부 응답을 그대로 신뢰하지 않는다(security.md S7).
     */
    @Transactional
    public void applyResult(UUID liveId, String status, String segmentsJson, String failureReason) {
        LiveSession session = sessionRepository.findOwnedAny(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        LiveCueSheetJpaEntity cueSheet = cueSheetRepository.findById(session.getId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        if (LiveCueSheetJpaEntity.STATUS_COMPLETED.equals(status)) {
            if (segmentsJson == null || segmentsJson.isBlank()) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT, "구간이 비어 있습니다.");
            }
            cueSheet.complete(segmentsJson);
        } else {
            cueSheet.fail(failureReason);
        }
    }

    private LiveSession loadOwned(UUID sellerId, UUID liveId) {
        return sessionRepository.findOwned(liveId, sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
