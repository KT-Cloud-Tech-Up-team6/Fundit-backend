package com.fundit.search.infrastructure.persistence.sellersummary.query;

import java.util.UUID;

/** SEARCH-007 응답 카드 형태로 바로 조회한다(persistence-convention.md §3). 사업자 연락처 등은 담지 않는다(security.md S9). */
public interface SellerCardProjection {

    UUID getSellerId();

    String getSellerDisplayName();

    Long getOngoingProjectCount();

    Long getTotalProjectCount();
}
