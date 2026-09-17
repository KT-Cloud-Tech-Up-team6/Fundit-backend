package com.fundit.search.presentation;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.search.application.SearchPageLimits;
import org.springframework.data.domain.PageRequest;

/** page/size 검증과 공통 최대 페이지 크기 제한을 한곳에서 적용한다. */
public final class SearchPageRequests {

    private SearchPageRequests() {
    }

    public static PageRequest of(int page, int size) {
        if (page < 0 || size < 1) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "page는 0 이상, size는 1 이상이어야 합니다.");
        }
        return PageRequest.of(page, Math.min(size, SearchPageLimits.MAX_SIZE));
    }
}
