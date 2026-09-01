package com.fundit.project.infrastructure.persistence.engagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OpenNotifyRequestJpaRepository extends JpaRepository<OpenNotifyRequestJpaEntity, Long> {

    void deleteByProjectIdAndMemberId(Long projectId, UUID memberId);

    List<OpenNotifyRequestJpaEntity> findByProjectId(Long projectId);

    long countByProjectId(Long projectId);

    /** 중복 무시 이유는 {@link ProjectFollowJpaRepository#insertIfAbsent}와 같다. */
    @Modifying
    @Query(value = """
            INSERT INTO project_open_notify_requests (project_id, member_id)
            VALUES (:projectId, :memberId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("projectId") Long projectId, @Param("memberId") UUID memberId);
}
