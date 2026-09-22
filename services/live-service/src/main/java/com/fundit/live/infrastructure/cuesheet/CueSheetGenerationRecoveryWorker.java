package com.fundit.live.infrastructure.cuesheet;

import com.fundit.live.infrastructure.persistence.cuesheet.LiveCueSheetJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 정체된 큐시트 생성을 FAILED로 풀어준다. {@code CueSheetService}는 AI 호출을 인메모리
 * {@code ApplicationEventPublisher}로 넘기므로, 커밋 후~AI 호출 사이 서버가 죽으면 이벤트가
 * 유실되고 행은 GENERATING에 영영 남는다 — 그러면 {@code requestGeneration}의 중복요청 가드가
 * 재생성 버튼까지 막는다({@code CueSheetService.java} 참고).
 *
 * <p>자동 재시도는 하지 않는다 — 재시도에 필요한 나머지 요청 필드(demoAvailable 등)를 영속화하는
 * 아웃박스가 없다. 대신 막힌 상태만 풀어 사람이 재생성 버튼을 다시 누를 수 있게 한다(YAGNI —
 * 크래시가 잦아 이걸로 부족해지면 그때 요청 페이로드 영속화 + 자동 재시도로 넓힌다).
 *
 * <p>{@code LiveEventOutboxWorker}와 같은 패턴 — 벌크 정리 작업은 도메인 리포지토리 포트를 거치지
 * 않고 인프라 컴포넌트가 JPA 레포지토리를 직접 쓴다.
 */
@Component
public class CueSheetGenerationRecoveryWorker {

    private static final Logger log = LoggerFactory.getLogger(CueSheetGenerationRecoveryWorker.class);
    private static final String STALE_REASON = "AI 응답이 지연되어 실패 처리되었습니다. 다시 시도해주세요.";

    private final LiveCueSheetJpaRepository cueSheetRepository;
    private final long staleAfterMs;

    public CueSheetGenerationRecoveryWorker(LiveCueSheetJpaRepository cueSheetRepository,
                                            @Value("${live.cuesheet-ai.stale-after-ms:240000}") long staleAfterMs) {
        this.cueSheetRepository = cueSheetRepository;
        this.staleAfterMs = staleAfterMs;
    }

    /** initialDelay 없이도 기동 직후 첫 실행이 돈다 — {@code LiveEventOutboxWorker}와 동일. */
    @Scheduled(fixedDelayString = "${live.cuesheet-ai.recovery-interval-ms:60000}")
    @Transactional
    public void failStale() {
        int failed = cueSheetRepository.failStaleGenerating(
                Instant.now().minusMillis(staleAfterMs), STALE_REASON);
        if (failed > 0) {
            log.warn("정체된 큐시트 생성 {}건을 FAILED로 전이", failed);
        }
    }
}
