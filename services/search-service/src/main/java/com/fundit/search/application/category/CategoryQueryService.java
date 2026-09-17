package com.fundit.search.application.category;

import com.fundit.common.error.BusinessException;
import com.fundit.search.domain.SearchErrorCode;
import com.fundit.search.infrastructure.persistence.category.CategoryJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectCardProjection;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SEARCH-003/004. 카테고리 대/중분류 트리 조회 + 카테고리별 프로젝트 목록 조회. */
@Service
@RequiredArgsConstructor
public class CategoryQueryService {

    private final CategoryJpaRepository categoryJpaRepository;
    private final ProjectDocumentJpaRepository projectDocumentJpaRepository;

    /**
     * 대분류별로 묶어 중분류를 display_order 오름차순으로 반환한다.
     * "대분류 등록 순서 유지"(SearchDomainApiSpec.md #3)를 보장할 별도 순서 컬럼이 없어(SearchERD.md 5-④),
     * 카테고리 전체 체계 확정 전까지는 대분류명 오름차순을 임시로 쓴다[가정].
     */
    @Transactional(readOnly = true)
    public List<CategoryGroup> getCategoryTree() {
        Map<String, List<CategoryMinorItem>> grouped = new LinkedHashMap<>();
        for (var row : categoryJpaRepository.findAllByOrderByCategoryMajorAscDisplayOrderAsc()) {
            grouped.computeIfAbsent(row.getCategoryMajor(), k -> new ArrayList<>())
                    .add(new CategoryMinorItem(row.getCategoryMinor(), row.getDisplayOrder()));
        }
        return grouped.entrySet().stream()
                .map(e -> new CategoryGroup(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * SEARCH-004. 존재하지 않는 categoryMajor/categoryMinor 조합은 SearchErrorCode.INVALID_CATEGORY(400).
     * status='ONGOING'만 대상으로 한다[가정 — PRD 10.2가 종료 프로젝트 포함 여부를 명시하지 않음].
     */
    @Transactional(readOnly = true)
    public Page<ProjectCardProjection> getProjectsByCategory(
            String categoryMajor, String categoryMinor, ProjectSortType sortType, PageRequest pageRequest) {
        validateCategory(categoryMajor, categoryMinor);
        var pageable = pageRequest.withSort(sortType.toSort());
        if (categoryMinor == null || categoryMinor.isBlank()) {
            return projectDocumentJpaRepository.findByStatusAndCategoryMajorAndDeletedAtIsNull(
                    ProjectDocumentStatus.ONGOING, categoryMajor, pageable);
        }
        return projectDocumentJpaRepository.findByStatusAndCategoryMajorAndCategoryMinorAndDeletedAtIsNull(
                ProjectDocumentStatus.ONGOING, categoryMajor, categoryMinor, pageable);
    }

    private void validateCategory(String categoryMajor, String categoryMinor) {
        boolean exists = (categoryMinor == null || categoryMinor.isBlank())
                ? categoryJpaRepository.existsByCategoryMajor(categoryMajor)
                : categoryJpaRepository.existsByCategoryMajorAndCategoryMinor(categoryMajor, categoryMinor);
        if (!exists) {
            throw new BusinessException(SearchErrorCode.INVALID_CATEGORY);
        }
    }

    public record CategoryGroup(String categoryMajor, List<CategoryMinorItem> categoryMinors) {
    }

    public record CategoryMinorItem(String categoryMinor, Integer displayOrder) {
    }
}
