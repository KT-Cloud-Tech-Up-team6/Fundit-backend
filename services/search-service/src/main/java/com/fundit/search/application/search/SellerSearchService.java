package com.fundit.search.application.search;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.search.infrastructure.persistence.searchquerylog.SearchQueryLogJpaEntity;
import com.fundit.search.infrastructure.persistence.searchquerylog.SearchQueryLogJpaRepository;
import com.fundit.search.infrastructure.persistence.sellersummary.SellerSummaryJpaRepository;
import com.fundit.search.infrastructure.persistence.sellersummary.query.SellerCardProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * SEARCH-007. 판매자 탭 통합검색. SEARCH-008(최근검색어 자동저장)은 SEARCH-005(상품 탭)에만
 * 연결된 부수 효과라 여기서는 검색 로그만 남긴다.
 */
@Service
@RequiredArgsConstructor
public class SellerSearchService {

    private final SellerSummaryJpaRepository sellerSummaryJpaRepository;
    private final SearchQueryLogJpaRepository searchQueryLogJpaRepository;

    public Page<SellerCardProjection> search(String keyword, PageRequest pageRequest) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "keyword는 1자 이상이어야 합니다.");
        }
        var result = sellerSummaryJpaRepository.searchByKeyword(keyword, pageRequest);
        searchQueryLogJpaRepository.save(SearchQueryLogJpaEntity.builder()
                .keyword(keyword)
                .resultCount((int) result.getTotalElements())
                .build());
        return result;
    }
}
