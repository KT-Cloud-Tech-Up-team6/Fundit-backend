package com.fundit.search.presentation.dto;

import java.util.List;

public record CategoryTreeResponse(List<CategoryGroup> categories) {

    public record CategoryGroup(String categoryMajor, List<CategoryMinorItem> categoryMinors) {
    }

    public record CategoryMinorItem(String categoryMinor, Integer displayOrder) {
    }
}
