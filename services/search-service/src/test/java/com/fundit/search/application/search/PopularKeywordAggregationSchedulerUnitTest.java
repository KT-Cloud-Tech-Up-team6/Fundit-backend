package com.fundit.search.application.search;

import com.fundit.search.infrastructure.persistence.popularkeyword.PopularSearchKeywordJpaEntity;
import com.fundit.search.infrastructure.persistence.popularkeyword.PopularSearchKeywordJpaRepository;
import com.fundit.search.infrastructure.persistence.searchquerylog.SearchQueryLogJpaRepository;
import com.fundit.search.infrastructure.persistence.searchquerylog.query.KeywordCountProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PopularKeywordAggregationSchedulerUnitTest {

    @Mock
    private SearchQueryLogJpaRepository searchQueryLogJpaRepository;
    @Mock
    private PopularSearchKeywordJpaRepository popularSearchKeywordJpaRepository;

    @InjectMocks
    private PopularKeywordAggregationScheduler scheduler;

    private KeywordCountProjection row(String keyword, long count) {
        return new KeywordCountProjection() {
            @Override
            public String getKeyword() {
                return keyword;
            }

            @Override
            public Long getSearchCount() {
                return count;
            }
        };
    }

    @Test
    void 집계_결과를_검색_횟수_내림차순_순위로_재적재한다() {
        // given
        when(searchQueryLogJpaRepository.aggregateTopKeywords(any(), anyInt()))
                .thenReturn(List.of(row("무선 이어폰", 50), row("캠핑 의자", 30)));

        // when
        scheduler.run();

        // then
        verify(popularSearchKeywordJpaRepository).deleteAllInBatch();
        var captor = ArgumentCaptor.forClass(List.class);
        verify(popularSearchKeywordJpaRepository).saveAll(captor.capture());
        @SuppressWarnings("unchecked")
        List<PopularSearchKeywordJpaEntity> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getRank()).isEqualTo(1);
        assertThat(saved.get(0).getKeyword()).isEqualTo("무선 이어폰");
        assertThat(saved.get(1).getRank()).isEqualTo(2);
    }

    @Test
    void 집계_대상_로그가_없으면_빈_스냅샷으로_초기화한다() {
        // given
        when(searchQueryLogJpaRepository.aggregateTopKeywords(any(), anyInt())).thenReturn(List.of());

        // when
        scheduler.run();

        // then
        verify(popularSearchKeywordJpaRepository).deleteAllInBatch();
        verify(popularSearchKeywordJpaRepository).saveAll(List.of());
    }
}
