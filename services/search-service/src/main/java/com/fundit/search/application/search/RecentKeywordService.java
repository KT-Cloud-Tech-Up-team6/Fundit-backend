package com.fundit.search.application.search;

import com.fundit.search.infrastructure.persistence.recentkeyword.RecentSearchKeywordJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** SEARCH-009. 본인 최근 검색어 조회/삭제 — member_id는 항상 인증 컨텍스트에서만 받는다(security.md S4). */
@Service
@RequiredArgsConstructor
public class RecentKeywordService {

    private static final int DEFAULT_SIZE = 10;

    private final RecentSearchKeywordJpaRepository recentSearchKeywordJpaRepository;

    @Transactional(readOnly = true)
    public List<RecentKeywordItem> getRecentKeywords(UUID memberId, Integer size) {
        int limit = (size == null || size < 1) ? DEFAULT_SIZE : size;
        return recentSearchKeywordJpaRepository.findByMemberIdOrderBySearchedAtDesc(memberId, PageRequest.of(0, limit))
                .stream()
                .map(e -> new RecentKeywordItem(e.getKeyword(), e.getSearchedAt()))
                .toList();
    }

    /** 존재하지 않는 키워드 삭제도 204로 처리한다(idempotent, member-service 찜 해제와 동일 원칙). */
    @Transactional
    public void deleteKeyword(UUID memberId, String keyword) {
        recentSearchKeywordJpaRepository.deleteByMemberIdAndKeyword(memberId, keyword);
    }

    @Transactional
    public void deleteAllKeywords(UUID memberId) {
        recentSearchKeywordJpaRepository.deleteByMemberId(memberId);
    }

    /** 필드가 API 응답과 1:1이라 presentation DTO를 따로 두지 않고 ContentResponse로 바로 감싼다. */
    public record RecentKeywordItem(String keyword, Instant searchedAt) {
    }
}
