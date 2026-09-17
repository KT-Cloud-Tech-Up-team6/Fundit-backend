package com.fundit.search.application.search;

import com.fundit.search.infrastructure.persistence.searchquerylog.SearchQueryLogJpaEntity;
import com.fundit.search.infrastructure.persistence.searchquerylog.SearchQueryLogJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchQueryLogEventHandlerUnitTest {

    @Mock
    private SearchQueryLogJpaRepository searchQueryLogJpaRepository;

    @InjectMocks
    private SearchQueryLogEventHandler handler;

    @Test
    void 검색_로그_이벤트를_저장한다() {
        // given
        var event = new SearchQueryLoggedEvent(null, "무선 이어폰", 3);

        // when
        handler.onSearchQueryLogged(event);

        // then
        var captor = ArgumentCaptor.forClass(SearchQueryLogJpaEntity.class);
        verify(searchQueryLogJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getKeyword()).isEqualTo("무선 이어폰");
        assertThat(captor.getValue().getResultCount()).isEqualTo(3);
    }

    @Test
    void 저장_실패는_예외를_전파하지_않는다() {
        // given
        doThrow(new RuntimeException("DB 오류")).when(searchQueryLogJpaRepository).save(any());

        // when
        Throwable thrown = catchThrowable(
                () -> handler.onSearchQueryLogged(new SearchQueryLoggedEvent(null, "키워드", 0)));

        // then
        assertThat(thrown).isNull();
    }
}
