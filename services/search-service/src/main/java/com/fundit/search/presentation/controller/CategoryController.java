package com.fundit.search.presentation.controller;

import com.fundit.search.application.category.CategoryQueryService;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectCardProjection;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectSortType;
import com.fundit.search.presentation.SearchPageRequests;
import com.fundit.search.presentation.dto.CategoryTreeResponse;
import com.fundit.search.presentation.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryQueryService categoryQueryService;

    @GetMapping("/categories")
    public CategoryTreeResponse getCategories() {
        var groups = categoryQueryService.getCategoryTree().stream()
                .map(g -> new CategoryTreeResponse.CategoryGroup(g.categoryMajor(),
                        g.categoryMinors().stream()
                                .map(m -> new CategoryTreeResponse.CategoryMinorItem(m.categoryMinor(), m.displayOrder()))
                                .toList()))
                .toList();
        return new CategoryTreeResponse(groups);
    }

    @GetMapping("/categories/{categoryMajor}/projects")
    public PageResponse<ProjectCardProjection> getProjectsByCategory(
            @PathVariable String categoryMajor,
            @RequestParam(required = false) String categoryMinor,
            @RequestParam(defaultValue = "POPULAR") ProjectSortType sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = categoryQueryService.getProjectsByCategory(
                categoryMajor, categoryMinor, sort, SearchPageRequests.of(page, size));
        return PageResponse.from(result);
    }
}
