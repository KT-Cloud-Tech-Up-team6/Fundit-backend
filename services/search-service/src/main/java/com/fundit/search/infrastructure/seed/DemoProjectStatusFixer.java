package com.fundit.search.infrastructure.seed;

import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * dev 전용 시연 데이터(#202) 보정 — 시연 프로젝트 문서를 {@code SUCCEEDED}로 맞춘다.
 *
 * <p>project 시연 시더는 성립된 프로젝트를 넣지만, 색인 최초 적재는 상태를 {@code ONGOING}으로 고정하고
 * {@code SUCCEEDED}는 {@code funding.succeeded.v1}로만 바뀐다. 시연 데이터엔 그 이벤트가 없다(가짜로 내면
 * fulfillment·payment까지 반응한다). 색인은 project 이벤트로 비동기로 들어오므로 기동 1회가 아니라 주기로 확인하고,
 * 맞춘 뒤로는 아무것도 하지 않는다.
 *
 * <p>완료 플래그는 트랜잭션이 <b>커밋된 뒤에만</b> 세운다 — {@link TransactionTemplate#execute}는 커밋까지 끝내고
 * 돌아오므로, 커밋이 실패하면 예외가 나고 플래그가 안 서서 다음 주기에 다시 시도한다(PR 리뷰 지적).
 *
 * <p>{@link #DEMO_PROJECT_ID}는 project·order·fulfillment 시연 시더와 같은 값이다(4곳 동일).
 *
 * <p>ponytail: dev 전용 보정. 시연 데이터가 성립 이벤트 경로로 만들어지게 되면 삭제한다.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DemoProjectStatusFixer {

    static final UUID DEMO_PROJECT_ID = UUID.fromString("f8c82570-d800-3d07-9dbf-82b091690ce5");

    private final ProjectDocumentJpaRepository projectDocumentRepository;
    private final TransactionTemplate transactionTemplate;
    private volatile boolean done;

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void fix() {
        if (done) {
            return;
        }
        Boolean indexed = transactionTemplate.execute(status ->
                projectDocumentRepository.findByProjectPublicId(DEMO_PROJECT_ID).map(document -> {
                    if (document.getStatus() != ProjectDocumentStatus.SUCCEEDED) {
                        projectDocumentRepository.updateStatus(document.getProjectId(), ProjectDocumentStatus.SUCCEEDED);
                        log.info("시연 프로젝트 색인 상태를 SUCCEEDED로 맞췄다 publicId={}", DEMO_PROJECT_ID);
                    }
                    return true;
                }).orElse(false));
        done = Boolean.TRUE.equals(indexed);
    }
}
