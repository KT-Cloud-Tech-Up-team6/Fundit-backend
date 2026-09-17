package com.fundit.search.application.search;

import com.fundit.common.error.BusinessException;
import com.fundit.search.infrastructure.persistence.searchquerylog.SearchQueryLogJpaEntity;
import com.fundit.search.infrastructure.persistence.searchquerylog.SearchQueryLogJpaRepository;
import com.fundit.search.infrastructure.persistence.sellersummary.SellerSummaryJpaRepository;
import com.fundit.search.infrastructure.persistence.sellersummary.query.SellerCardProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerSearchServiceUnitTest {

    @Mock
    private SellerSummaryJpaRepository sellerSummaryJpaRepository;
    @Mock
    private SearchQueryLogJpaRepository searchQueryLogJpaRepository;

    @InjectMocks
    private SellerSearchService sellerSearchService;

    @Test
    void 빈_키워드는_INVALID_INPUT_예외가_발생한다() {
        assertThatThrownBy(() -> sellerSearchService.search(" ", PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 검색이_실행되면_결과건수와_함께_로그가_적재된다() {
        // given
        var page = new PageImpl<SellerCardProjection>(List.of(), PageRequest.of(0, 20), 2);
        when(sellerSummaryJpaRepository.searchByKeyword(eq("프라이팬"), any())).thenReturn(page);

        // when
        sellerSearchService.search("프라이팬", PageRequest.of(0, 20));

        // then
        var captor = ArgumentCaptor.forClass(SearchQueryLogJpaEntity.class);
        verify(searchQueryLogJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getResultCount()).isEqualTo(2);
        assertThat(captor.getValue().getKeyword()).isEqualTo("프라이팬");
    }
}
