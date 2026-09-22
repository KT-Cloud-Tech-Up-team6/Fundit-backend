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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * AI 큐시트 생성/조회/수정(요구사항정의서 6.2.4.2).
 *
 * <p>{@code jobId}를 따로 발급하지 않는다 — 세션당 큐시트가 1개라 {@code GET}의 status로
 * 폴링하면 충분하고, 별도 식별자는 조회 경로가 없어 쓸 데가 없다.
 *
 * <p><b>AI 호출은 요청 스레드에서 하지 않는다.</b> 최대 3분+ 걸릴 수 있어(큐시트 담당 합의,
 * 2026-09-22) 판매자의 생성 요청은 {@code GENERATING} 저장 직후 바로 응답하고, 실제 AI 호출은
 * {@link #onCueSheetGenerationRequested}가 커밋 후 별도 스레드에서 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CueSheetService {

    /** 요구사항정의서 6.2.3: AI 큐시트는 방송 길이 10분 이내로 구성한다. */
    private static final int MAX_DURATION_SEC = 600;

    private final LiveCueSheetRepository cueSheetRepository;
    private final LiveSessionRepository sessionRepository;
    private final AiClient aiClient;
    private final AiProductContextAssembler productContextAssembler;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

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
        AiProductContextAssembler.CueSheetInput input = productContextAssembler.forCueSheet(session);

        // 재생성은 기존 행을 덮어쓴다 — 이력 보관 요구가 없다.
        cueSheetRepository.save(LiveCueSheet.requestGeneration(session.getId(), mode, targetDurationSec));

        // 여기서 AI를 직접 부르지 않는다 — 판매자 요청이 AI 응답(최대 3분+)을 기다리게 된다.
        // 이벤트를 발행해 커밋 후 별도 스레드에서 처리한다(onCueSheetGenerationRequested).
        eventPublisher.publishEvent(new CueSheetGenerationRequested(liveId, new AiClient.CueSheetRequest(
                mode, targetDurationSec, demoAvailable, emphasisPoints, tone, mandatoryPhrases,
                input.product(), input.funding())));
    }

    /**
     * package-private — 테스트에서 Spring 이벤트 시스템 없이 직접 호출하기 위해 접근 제한을 풀어둔다
     * ({@code LiveStreamService.markAiPrepared}와 같은 이유).
     *
     * <p>{@code applyResult}를 {@code this.applyResult(...)}로 직접 부르면 프록시를 거치지 않아
     * {@code @Transactional}이 적용되지 않는다(Spring AOP 셀프 호출 함정, 리뷰 지적으로 발견) —
     * {@link #applyResultInNewTransaction}이 {@code TransactionTemplate}으로 직접 경계를 연다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCueSheetGenerationRequested(CueSheetGenerationRequested event) {
        try {
            String segments = aiClient.requestCueSheet(event.liveId().toString(), event.request());
            applyResultInNewTransaction(event.liveId(), GenerationStatus.COMPLETED.name(), segments, null);
        } catch (RuntimeException e) {
            log.warn("큐시트 생성 실패, liveId={}", event.liveId(), e);
            applyResultInNewTransaction(event.liveId(), GenerationStatus.FAILED.name(), null, failureReasonOf(e));
        }
    }

    /**
     * {@code applyResult}의 {@code @Transactional}은 self-invocation 경로에서 무시되므로
     * (같은 이유로 {@code LiveStreamService.markAiPrepared}도 {@code TransactionTemplate}을 쓴다)
     * 여기서 직접 트랜잭션을 연다. {@code @Async} 스레드에는 활성 트랜잭션이 없어 기본 전파
     * (REQUIRED)로도 새 트랜잭션이 열린다 — REQUIRES_NEW는 이미 진행 중인 트랜잭션에 합류하지
     * 않으려 할 때만 필요하다.
     */
    private void applyResultInNewTransaction(UUID liveId, String status, String segmentsJson, String failureReason) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(txStatus -> applyResult(liveId, status, segmentsJson, failureReason));
    }

    /**
     * {@code DependencyFailureException.getMessage()}는 실제 사유가 아니라
     * {@code CommonErrorCode}의 고정 문구를 돌려준다({@code error-handling.md} 규칙 — 임의 문자열
     * 코드/메시지를 그대로 노출하지 않는다) — 원인은 {@code getCause()}에 있다.
     */
    private static String failureReasonOf(RuntimeException e) {
        return e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
    }

    record CueSheetGenerationRequested(UUID liveId, AiClient.CueSheetRequest request) {
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
     * AI 호출 결과를 적용한다 — {@link #applyResultInNewTransaction}을 통해서만 내부에서 불린다
     * (더 이상 외부 콜백 경로가 아니다). 구조·길이를 검증한 뒤 저장한다(security.md S7).
     *
     * <p>모르는 status를 FAILED로 굳히지 않는다 — AI가 {@code "PROCESSING"}을 보내면
     * 실패로 저장되고 되돌릴 경로가 없다. 아는 값 둘만 받고 나머지는 예외로 던진다
     * (지금 호출부는 항상 COMPLETED/FAILED만 넘기므로 이 분기는 방어용이다).
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
