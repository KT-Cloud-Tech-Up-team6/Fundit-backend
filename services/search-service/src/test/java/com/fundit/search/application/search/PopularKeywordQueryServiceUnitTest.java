package com.fundit.search.application.search;

import com.fundit.search.infrastructure.persistence.popularkeyword.PopularSearchKeywordJpaEntity;
import com.fundit.search.infrastructure.persistence.popularkeyword.PopularSearchKeywordJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PopularKeywordQueryServiceUnitTest {

    @Mock
    private PopularSearchKeywordJpaRepository popularSearchKeywordJpaRepository;

    @InjectMocks
    private PopularKeywordQueryService popularKeywordQueryService;

    @Test
    void 랭크_오름차순으로_반환한다() {
        // given
        when(popularSearchKeywordJpaRepository.findAllByOrderByRankAsc()).thenReturn(List.of(
                PopularSearchKeywordJpaEntity.builder().rank(1).keyword("무선 이어폰").searchCount(50).aggregatedAt(Instant.now()).build(),
                PopularSearchKeywordJpaEntity.builder().rank(2).keyword("캠핑 의자").searchCount(30).aggregatedAt(Instant.now()).build()
        ));

        // when
        var result = popularKeywordQueryService.getPopularKeywords();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).rank()).isEqualTo(1);
        assertThat(result.get(0).keyword()).isEqualTo("무선 이어폰");
    }

    @Test
    void 집계된_적이_없으면_빈_목록을_반환한다() {
        // given
        when(popularSearchKeywordJpaRepository.findAllByOrderByRankAsc()).thenReturn(List.of());

        // when
        var result = popularKeywordQueryService.getPopularKeywords();

        // then
        assertThat(result).isEmpty();
    }
}
