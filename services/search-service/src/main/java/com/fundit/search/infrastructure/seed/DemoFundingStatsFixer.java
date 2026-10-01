package com.fundit.search.infrastructure.seed;

import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * dev 전용 목업 프로젝트(#216) 카드 통계 보정 — project {@code MockProjectSeeder}가 스냅샷으로 직접
 * 넣은 모금액·참여자 수를 색인에도 같은 값으로 맞춘다.
 *
 * <p>목업 프로젝트엔 주문이 없어 order 배치 대상이 아니고({@code findDistinctProjectPublicIdsWithCountableFundings}),
 * 그래서 SEARCH-013 구독을 붙여도 이벤트가 영영 오지 않는다. 색인 최초 적재는 통계를 0으로 넣으므로
 * 보정 없이는 카드만 "0% 달성"으로 남는다.
 *
 * <p>달성률은 쓰지 않는다 — {@code updateFundingStatsIfUnset}이 색인 행의 goal_amount로 계산하고, 그 값이
 * project 목업 스냅샷의 달성률과 같다(50건 전부 확인).
 *
 * <p>{@code current_amount}가 0인 행만 고친다 — 나중에 실제 이벤트가 오면 그 값을 덮지 않는다. 이 조건은
 * {@code updateFundingStatsIfUnset}의 WHERE 절에 있다(아래 자바 검사는 불필요한 UPDATE를 줄이는 빠른 경로일 뿐) —
 * 읽은 뒤 UPDATE 사이에 SEARCH-013 이벤트가 커밋되는 경우까지 막으려면 조건이 SQL에 있어야 한다.
 * 색인은 project 이벤트로 비동기로 들어오므로 기동 1회가 아니라 주기로 확인하고, 50건이 전부 색인된
 * 뒤로는 아무것도 하지 않는다({@code DemoProjectStatusFixer}와 같은 형태).
 *
 * <p>ponytail: dev 전용 보정. 목업에 주문이 생겨 order 배치가 집계하게 되면 이 클래스와 시드 json을 삭제한다.
 */
@Slf4j
@Component
@Profile("dev")
public class DemoFundingStatsFixer {

    static final String SEED_FILE = "seed/mock-funding-stats.json";

    private final ProjectDocumentJpaRepository projectDocumentRepository;
    private final TransactionTemplate transactionTemplate;
    private final List<MockFundingStat> stats;
    private volatile boolean done;

    public DemoFundingStatsFixer(ProjectDocumentJpaRepository projectDocumentRepository,
                                 TransactionTemplate transactionTemplate,
                                 ObjectMapper objectMapper) {
        this.projectDocumentRepository = projectDocumentRepository;
        this.transactionTemplate = transactionTemplate;
        this.stats = read(objectMapper);
    }

    private static List<MockFundingStat> read(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<MockFundingStat>>() {
            });
        } catch (RuntimeException | IOException e) {
            // 기동은 막지 않는다 — 보정이 안 되면 카드 통계가 0으로 남을 뿐이다.
            log.warn("목업 펀딩 통계 시드 읽기 실패", e);
            return List.of();
        }
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void fix() {
        if (done || stats.isEmpty()) {
            return;
        }
        Boolean allIndexed = transactionTemplate.execute(status -> {
            int indexed = 0;
            int fixed = 0;
            for (MockFundingStat stat : stats) {
                var document = projectDocumentRepository.findByProjectPublicId(stat.publicId()).orElse(null);
                if (document == null) {
                    continue;
                }
                indexed++;
                if (document.getCurrentAmount() == 0
                        && projectDocumentRepository.updateFundingStatsIfUnset(
                                stat.publicId(), stat.currentAmount(), stat.participantCount()) > 0) {
                    fixed++;
                }
            }
            if (fixed > 0) {
                log.info("목업 프로젝트 색인 펀딩 통계를 보정했다 — {}건(색인 {}/{}건)", fixed, indexed, stats.size());
            }
            return indexed == stats.size();
        });
        done = Boolean.TRUE.equals(allIndexed);
    }

    record MockFundingStat(UUID publicId, long currentAmount, Integer participantCount) {
    }
}
