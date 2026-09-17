package com.fundit.search.application.search;

import com.fundit.search.infrastructure.persistence.popularkeyword.PopularSearchKeywordJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** SEARCH-010. 집계 배치(SEARCH-015)가 채워둔 스냅샷을 rank 순으로 그대로 읽기만 한다. */
@Service
@RequiredArgsConstructor
public class PopularKeywordQueryService {

    private final PopularSearchKeywordJpaRepository popularSearchKeywordJpaRepository;

    @Transactional(readOnly = true)
    public List<PopularKeywordItem> getPopularKeywords() {
        return popularSearchKeywordJpaRepository.findAllByOrderByRankAsc().stream()
                .map(e -> new PopularKeywordItem(e.getRank(), e.getKeyword()))
                .toList();
    }

    public record PopularKeywordItem(Integer rank, String keyword) {
    }
}
