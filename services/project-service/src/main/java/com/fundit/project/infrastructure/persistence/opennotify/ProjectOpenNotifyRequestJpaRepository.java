package com.fundit.project.infrastructure.persistence.opennotify;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectOpenNotifyRequestJpaRepository extends JpaRepository<ProjectOpenNotifyRequestJpaEntity, Long> {

    long countByProjectId(Long projectId);
}
