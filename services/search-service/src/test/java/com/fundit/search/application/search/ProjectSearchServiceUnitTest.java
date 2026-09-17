package com.fundit.search.application.search;

import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectCardProjection;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectSearchSubTab;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectSortType;
import com.fundit.search.infrastructure.persistence.recentkeyword.RecentSearchKeywordJpaRepository;
import com.fundit.search.infrastructure.persistence.searchquerylog.SearchQueryLogJpaEntity;
import com.fundit.search.infrastructure.persistence.searchquerylog.SearchQueryLogJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectSearchServiceUnitTest {

    @Mock
    private ProjectDocumentJpaRepository projectDocumentJpaRepository;
    @Mock
    private SearchQueryLogJpaRepository searchQueryLogJpaRepository;
    @Mock
    private RecentSearchKeywordJpaRepository recentSearchKeywordJpaRepository;

    @InjectMocks
    private ProjectSearchService projectSearchService;

    private Page<ProjectCardProjection> emptyPage() {
        return new PageImpl<>(List.of());
    }

    @Test
    void 로그인_회원이_검색하면_최근검색어가_저장된다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(projectDocumentJpaRepository.searchByKeyword(eq("무선 이어폰"), anyList(), any())).thenReturn(emptyPage());

        // when
        projectSearchService.search("무선 이어폰", ProjectSearchSubTab.ONGOING, ProjectSortType.POPULAR,
                memberId, PageRequest.of(0, 20));

        // then
        verify(recentSearchKeywordJpaRepository).upsert(memberId, "무선 이어폰");
        verify(recentSearchKeywordJpaRepository).deleteExceedingLimit(eq(memberId), eq(10));
    }

    @Test
    void 비로그인_검색은_최근검색어를_저장하지_않는다() {
        // given
        when(projectDocumentJpaRepository.searchByKeyword(eq("캠핑 의자"), anyList(), any())).thenReturn(emptyPage());

        // when
        projectSearchService.search("캠핑 의자", ProjectSearchSubTab.ONGOING, ProjectSortType.POPULAR,
                null, PageRequest.of(0, 20));

        // then
        verify(recentSearchKeywordJpaRepository, never()).upsert(any(), any());
    }

    @Test
    void 최근검색어_저장이_실패해도_검색_응답은_영향받지_않는다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(projectDocumentJpaRepository.searchByKeyword(eq("키워드"), anyList(), any())).thenReturn(emptyPage());
        org.mockito.Mockito.doThrow(new RuntimeException("DB 오류"))
                .when(recentSearchKeywordJpaRepository).upsert(memberId, "키워드");

        // when & then — 예외가 전파되지 않아야 한다
        var result = projectSearchService.search("키워드", ProjectSearchSubTab.ONGOING, ProjectSortType.POPULAR,
                memberId, PageRequest.of(0, 20));
        org.assertj.core.api.Assertions.assertThat(result).isNotNull();
    }

    @Test
    void ENDED_탭이면_SUCCEEDED와_FAILED_상태로_조회한다() {
        // given
        when(projectDocumentJpaRepository.searchByKeyword(eq("키워드"),
                eq(List.of(ProjectDocumentStatus.SUCCEEDED, ProjectDocumentStatus.FAILED)), any()))
                .thenReturn(emptyPage());

        // when
        projectSearchService.search("키워드", ProjectSearchSubTab.ENDED, ProjectSortType.POPULAR,
                null, PageRequest.of(0, 20));

        // then
        verify(projectDocumentJpaRepository).searchByKeyword(eq("키워드"),
                eq(List.of(ProjectDocumentStatus.SUCCEEDED, ProjectDocumentStatus.FAILED)), any());
    }

    @Test
    void 검색이_실행되면_결과건수와_함께_로그가_적재된다() {
        // given
        var page = new PageImpl<ProjectCardProjection>(List.of(), PageRequest.of(0, 20), 3);
        when(projectDocumentJpaRepository.searchByKeyword(eq("키워드"), anyList(), any())).thenReturn(page);

        // when
        projectSearchService.search("키워드", ProjectSearchSubTab.ONGOING, ProjectSortType.POPULAR,
                null, PageRequest.of(0, 20));

        // then
        var captor = org.mockito.ArgumentCaptor.forClass(SearchQueryLogJpaEntity.class);
        verify(searchQueryLogJpaRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getResultCount()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getKeyword()).isEqualTo("키워드");
    }
}
