package com.fundit.project.infrastructure.persistence.community;

import com.fundit.project.domain.community.PostType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityPostJpaRepository extends JpaRepository<CommunityPostJpaEntity, Long> {

    List<CommunityPostJpaEntity> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<CommunityPostJpaEntity> findByProjectIdAndPostTypeOrderByCreatedAtDesc(Long projectId, PostType postType);
}
