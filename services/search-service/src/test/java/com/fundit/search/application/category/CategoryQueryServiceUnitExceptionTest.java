package com.fundit.search.application.category;

import com.fundit.common.error.BusinessException;
import com.fundit.search.domain.SearchErrorCode;
import com.fundit.search.infrastructure.persistence.category.CategoryJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectSortType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryQueryServiceUnitExceptionTest {

    @Mock
    private CategoryJpaRepository categoryJpaRepository;
    @Mock
    private ProjectDocumentJpaRepository projectDocumentJpaRepository;

    @InjectMocks
    private CategoryQueryService categoryQueryService;

    @Test
    void 존재하지_않는_대분류로_조회하면_INVALID_CATEGORY_예외가_발생한다() {
        // given
        when(categoryJpaRepository.existsByCategoryMajor("없는분류")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> categoryQueryService.getProjectsByCategory(
                "없는분류", null, ProjectSortType.POPULAR, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SearchErrorCode.INVALID_CATEGORY);
    }

    @Test
    void 존재하지_않는_중분류_조합이면_INVALID_CATEGORY_예외가_발생한다() {
        // given
        when(categoryJpaRepository.existsByCategoryMajorAndCategoryMinor("테크·가전", "없는중분류")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> categoryQueryService.getProjectsByCategory(
                "테크·가전", "없는중분류", ProjectSortType.POPULAR, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SearchErrorCode.INVALID_CATEGORY);
    }
}
