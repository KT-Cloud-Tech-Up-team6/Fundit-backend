package com.fundit.search.application.search;

import com.fundit.common.error.BusinessException;
import com.fundit.search.infrastructure.persistence.sellersummary.SellerSummaryJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@ExtendWith(MockitoExtension.class)
class SellerSearchServiceUnitExceptionTest {

    @Mock
    private SellerSummaryJpaRepository sellerSummaryJpaRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SellerSearchService sellerSearchService;

    @Test
    void 빈_키워드는_INVALID_INPUT_예외가_발생한다() {
        // given
        String keyword = " ";

        // when
        Throwable thrown = catchThrowable(() -> sellerSearchService.search(keyword, PageRequest.of(0, 20)));

        // then
        assertThat(thrown).isInstanceOf(BusinessException.class);
    }
}
