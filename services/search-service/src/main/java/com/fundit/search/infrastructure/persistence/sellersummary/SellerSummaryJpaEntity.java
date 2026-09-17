package com.fundit.search.infrastructure.persistence.sellersummary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.time.Instant;
import java.util.UUID;

/**
 * SEARCH-007 판매자 탭이 읽는 {@code seller_summary} DB 뷰의 읽기 전용 매핑(SearchERD.md 7번).
 * {@code project_documents}를 seller_id로 집계한 결과라 별도 물리 테이블/동기화가 없다 —
 * 항상 Hibernate가 로딩만 하고 애플리케이션이 직접 생성하지 않으므로 빌더를 두지 않는다.
 *
 * <p>{@code @Table} 대신 {@code @Subselect} + {@code @Synchronize}를 쓰는 이유: 뷰가 실제로
 * project_documents 위에 있다는 걸 Hibernate에 알려줘야, 같은 트랜잭션에서 project_documents를
 * 먼저 쓰고 이 엔티티를 조회할 때 flush를 자동으로 끼워 넣는다 — 안 하면 아직 커밋 전 변경이
 * 조회에 반영되지 않는다.
 */
@Getter
@Entity
@Immutable
@Subselect("SELECT * FROM seller_summary")
@Synchronize("project_documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerSummaryJpaEntity {

    @Id
    @Column(name = "seller_id")
    private UUID sellerId;

    @Column(name = "seller_display_name")
    private String sellerDisplayName;

    @Column(name = "ongoing_project_count")
    private Long ongoingProjectCount;

    @Column(name = "total_project_count")
    private Long totalProjectCount;

    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;
}
