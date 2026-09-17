package com.fundit.search.application.search;

import com.fundit.search.infrastructure.persistence.searchquerylog.SearchQueryLogJpaEntity;
import com.fundit.search.infrastructure.persistence.searchquerylog.SearchQueryLogJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 검색 요청 스레드와 분리해 검색 로그를 적재한다. 저장 실패는 검색 응답에 전파하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchQueryLogEventHandler {

    private final SearchQueryLogJpaRepository searchQueryLogJpaRepository;

    @Async
    @EventListener
    public void onSearchQueryLogged(SearchQueryLoggedEvent event) {
        try {
            searchQueryLogJpaRepository.save(SearchQueryLogJpaEntity.builder()
                    .memberId(event.memberId())
                    .keyword(event.keyword())
                    .resultCount(event.resultCount())
                    .build());
        } catch (RuntimeException e) {
            log.warn("검색 로그 저장 실패 - keyword={}", event.keyword(), e);
        }
    }
}
