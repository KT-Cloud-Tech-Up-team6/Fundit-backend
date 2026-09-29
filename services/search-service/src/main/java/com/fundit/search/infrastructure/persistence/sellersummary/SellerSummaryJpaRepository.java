package com.fundit.search.infrastructure.persistence.sellersummary;

import com.fundit.search.infrastructure.persistence.sellersummary.query.SellerCardProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SellerSummaryJpaRepository extends JpaRepository<SellerSummaryJpaEntity, UUID> {

    /**
     * SEARCH-007. seller_display_name에 대한 pg_trgm 부분 유사도 매칭, 유사도 내림차순(security.md S1).
     * word_similarity·임계값 0.3을 쓰는 이유는 ProjectDocumentJpaRepository#searchByKeyword와 같다.
     */
    @Query("""
            SELECT s FROM SellerSummaryJpaEntity s
            WHERE function('word_similarity', :keyword, s.sellerDisplayName) > 0.3
            ORDER BY function('word_similarity', :keyword, s.sellerDisplayName) DESC
            """)
    Page<SellerCardProjection> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
