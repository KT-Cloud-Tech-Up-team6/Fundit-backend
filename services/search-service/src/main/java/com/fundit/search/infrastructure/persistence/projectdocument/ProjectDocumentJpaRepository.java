package com.fundit.search.infrastructure.persistence.projectdocument;

import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectCardProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProjectDocumentJpaRepository extends JpaRepository<ProjectDocumentJpaEntity, Long> {

    /**
     * SEARCH-001 홈피드. 인기순(participant_count DESC, wish_count DESC)만 지원한다 — API 계약에
     * sort 파라미터가 없어 신규순 대안은 노출하지 않는다[가정, SearchDomainFunctionalSpec.md SEARCH-001 참고].
     */
    List<ProjectCardProjection> findByStatusAndDeletedAtIsNullOrderByParticipantCountDescWishCountDesc(
            ProjectDocumentStatus status, Pageable pageable);

    /** SEARCH-004. categoryMinor 미지정 시 대분류 전체 대상. 정렬은 Pageable에 담긴 Sort(ProjectSortType)를 그대로 쓴다. */
    Page<ProjectCardProjection> findByStatusAndCategoryMajorAndDeletedAtIsNull(
            ProjectDocumentStatus status, String categoryMajor, Pageable pageable);

    Page<ProjectCardProjection> findByStatusAndCategoryMajorAndCategoryMinorAndDeletedAtIsNull(
            ProjectDocumentStatus status, String categoryMajor, String categoryMinor, Pageable pageable);

    /**
     * SEARCH-005. title/seller_display_name에 대한 pg_trgm 유사도 매칭(security.md S1 — 키워드는
     * 바인딩 변수로만 전달). 쿼리가 집계 루트(p)를 그대로 select하므로 Pageable의 Sort가 반환 타입인
     * ProjectCardProjection에 정상 적용된다(persistence-convention.md §3).
     *
     * <p>유사도 임계값 0.1은 조정 가능한 잠정값이다 — 정확도 이슈가 실제로 발생하면
     * Elasticsearch 도입을 재검토한다(SearchERD.md 설계 결정 1번).
     */
    @Query("""
            SELECT p FROM ProjectDocumentJpaEntity p
            WHERE p.deletedAt IS NULL
              AND p.status IN :statuses
              AND (function('similarity', p.title, :keyword) > 0.1
                   OR function('similarity', p.sellerDisplayName, :keyword) > 0.1)
            """)
    Page<ProjectCardProjection> searchByKeyword(
            @Param("keyword") String keyword, @Param("statuses") List<ProjectDocumentStatus> statuses, Pageable pageable);

    /** SEARCH-012. 영향 행이 0이면 색인에 없는 projectId라는 뜻이다 — 호출부가 로그로 확인 대상 표시. */
    @Modifying
    @Query("UPDATE ProjectDocumentJpaEntity p SET p.status = :status WHERE p.projectId = :projectId")
    int updateStatus(@Param("projectId") Long projectId, @Param("status") ProjectDocumentStatus status);

    /**
     * SEARCH-014 멱등 가드. project-service {@code ProjectWishStatJpaRepository}와 동일하게, 이 리포지토리가
     * project_documents 애그리거트 하나만 다루더라도 그 위에 얹힌 보조 테이블(search_wish_stat_members)
     * 쿼리를 같이 둔다 — 별도 엔티티/리포지토리를 만들 필요가 없는 경우다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO search_wish_stat_members (project_id, member_id) VALUES (:projectId, :memberId) "
            + "ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertWishMemberIfAbsent(@Param("projectId") Long projectId, @Param("memberId") UUID memberId);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM search_wish_stat_members WHERE project_id = :projectId AND member_id = :memberId",
            nativeQuery = true)
    int deleteWishMember(@Param("projectId") Long projectId, @Param("memberId") UUID memberId);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE project_documents SET wish_count = wish_count + 1 WHERE project_id = :projectId",
            nativeQuery = true)
    void incrementWishCount(@Param("projectId") Long projectId);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE project_documents SET wish_count = GREATEST(wish_count - 1, 0) WHERE project_id = :projectId",
            nativeQuery = true)
    void decrementWishCount(@Param("projectId") Long projectId);

    /**
     * SEARCH-011. project.approved.v1/project.updated.v1 공용 upsert — "존재하지 않는 project_id에
     * 대한 갱신 이벤트 수신 → 신규 행으로 생성(순서 역전 대비)"를 이 쿼리 하나로 만족시킨다
     * (SearchDomainFunctionalSpec.md SEARCH-011 예외 처리).
     *
     * <p>DO UPDATE 절이 status/current_amount/achievement_rate/participant_count/wish_count/deleted_at을
     * 건드리지 않는 게 핵심이다 — 이 컬럼들은 SEARCH-012/013/014가 각자 관리하는 값이라, 여기서 같이
     * 덮어쓰면 project.updated.v1(제목 수정 등) 수신만으로 이미 쌓인 펀딩 통계·찜수가 초기화된다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO project_documents (
                project_id, project_public_id, seller_id, seller_display_name, title, thumbnail_url,
                category_major, category_minor, status, goal_amount, funding_start_at, funding_deadline,
                project_created_at, current_amount, achievement_rate, participant_count, wish_count, indexed_at
            ) VALUES (
                :projectId, :publicId, :sellerId, :sellerDisplayName, :title, :thumbnailUrl,
                :categoryMajor, :categoryMinor, 'ONGOING', :goalAmount, :fundingStartAt, :fundingDeadline,
                :projectCreatedAt, 0, 0, 0, 0, now()
            )
            ON CONFLICT (project_id) DO UPDATE SET
                project_public_id = EXCLUDED.project_public_id,
                seller_id = EXCLUDED.seller_id,
                seller_display_name = EXCLUDED.seller_display_name,
                title = EXCLUDED.title,
                thumbnail_url = EXCLUDED.thumbnail_url,
                category_major = EXCLUDED.category_major,
                category_minor = EXCLUDED.category_minor,
                goal_amount = EXCLUDED.goal_amount,
                funding_start_at = EXCLUDED.funding_start_at,
                funding_deadline = EXCLUDED.funding_deadline,
                project_created_at = EXCLUDED.project_created_at,
                indexed_at = now()
            """, nativeQuery = true)
    void upsertProjectInfo(
            @Param("projectId") Long projectId, @Param("publicId") UUID publicId, @Param("sellerId") UUID sellerId,
            @Param("sellerDisplayName") String sellerDisplayName, @Param("title") String title,
            @Param("thumbnailUrl") String thumbnailUrl, @Param("categoryMajor") String categoryMajor,
            @Param("categoryMinor") String categoryMinor, @Param("goalAmount") Long goalAmount,
            @Param("fundingStartAt") Instant fundingStartAt, @Param("fundingDeadline") Instant fundingDeadline,
            @Param("projectCreatedAt") Instant projectCreatedAt);
}
