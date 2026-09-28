package com.fundit.project.application.pagesummary;

import com.fundit.project.domain.pagesummary.PageSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 요약 생성·폴링 워커. 프로젝트마다 별도 트랜잭션({@link PageSummaryService#process})으로 처리해 한 건의
 * AI 오류가 나머지를 막지 않는다.
 * ponytail: 행 잠금 없이 단일 인스턴스를 가정한다(다른 outbox 워커와 동일). 여러 파드가 같은 행을 잡아도
 * AI는 같은 멱등키 요청에 같은 run을 돌려준다. 파드를 늘리면 FOR UPDATE SKIP LOCKED로 바꾼다.
 */
@Component
@ConditionalOnProperty(prefix = "page-summary", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class PageSummaryWorker {

    private static final Logger log = LoggerFactory.getLogger(PageSummaryWorker.class);

    private final PageSummaryRepository pageSummaryRepository;
    private final PageSummaryService pageSummaryService;
    private final int batchSize;

    public PageSummaryWorker(PageSummaryRepository pageSummaryRepository, PageSummaryService pageSummaryService,
                             @Value("${page-summary.batch-size:20}") int batchSize) {
        this.pageSummaryRepository = pageSummaryRepository;
        this.pageSummaryService = pageSummaryService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${page-summary.poll-interval-ms:5000}")
    public void run() {
        for (Long projectId : pageSummaryRepository.findDueProjectIds(Instant.now(), batchSize)) {
            try {
                pageSummaryService.process(projectId);
            } catch (RuntimeException e) {
                log.warn("상세 요약 처리 실패, 다음 주기에 재시도합니다. projectId={}", projectId, e);
            }
        }
    }
}
