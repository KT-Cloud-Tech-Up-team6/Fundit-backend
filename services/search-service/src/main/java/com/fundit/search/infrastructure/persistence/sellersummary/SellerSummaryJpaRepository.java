package com.fundit.search.infrastructure.persistence.sellersummary;

import com.fundit.search.infrastructure.persistence.sellersummary.query.SellerCardProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SellerSummaryJpaRepository extends JpaRepository<SellerSummaryJpaEntity, UUID> {

    /** SEARCH-007. seller_display_name에 대한 pg_trgm 유사도 매칭, 유사도 내림차순(security.md S1). */
    @Query("""
            SELECT s FROM SellerSummaryJpaEntity s
            WHERE function('similarity', s.sellerDisplayName, :keyword) > 0.1
            ORDER BY function('similarity', s.sellerDisplayName, :keyword) DESC
            """)
    Page<SellerCardProjection> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
