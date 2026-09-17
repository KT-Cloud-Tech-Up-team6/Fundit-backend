package com.fundit.search.application.search;

import com.fundit.search.infrastructure.persistence.sellersummary.SellerSummaryJpaRepository;
import com.fundit.search.infrastructure.persistence.sellersummary.query.SellerCardProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerSearchServiceUnitTest {

    @Mock
    private SellerSummaryJpaRepository sellerSummaryJpaRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SellerSearchService sellerSearchService;

    @Test
    void 검색이_실행되면_결과건수와_함께_로그_이벤트가_발행된다() {
        // given
        var page = new PageImpl<SellerCardProjection>(List.of(), PageRequest.of(0, 20), 2);
        when(sellerSummaryJpaRepository.searchByKeyword(eq("프라이팬"), any())).thenReturn(page);

        // when
        sellerSearchService.search("프라이팬", PageRequest.of(0, 20));

        // then
        verify(eventPublisher).publishEvent(new SearchQueryLoggedEvent(null, "프라이팬", 2));
    }
}
