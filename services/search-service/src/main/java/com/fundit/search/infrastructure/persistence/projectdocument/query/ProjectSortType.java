package com.fundit.search.infrastructure.persistence.projectdocument.query;

import org.springframework.data.domain.Sort;

/** 카테고리 탐색(SEARCH-004)·통합검색(SEARCH-005/006)이 공용으로 쓰는 정렬 기준. */
public enum ProjectSortType {
    POPULAR,
    RECENT,
    DEADLINE;

    /** SearchERD.md 인덱스 3종(popularity/created/deadline)에 그대로 대응한다. */
    public Sort toSort() {
        return switch (this) {
            case POPULAR -> Sort.by(Sort.Order.desc("participantCount"), Sort.Order.desc("wishCount"));
            case RECENT -> Sort.by(Sort.Order.desc("projectCreatedAt"));
            case DEADLINE -> Sort.by(Sort.Order.asc("fundingDeadline"));
        };
    }
}
