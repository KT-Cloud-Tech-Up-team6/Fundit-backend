package com.fundit.project.domain.project;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {

    Project save(Project project);

    /** 소프트 삭제된 프로젝트는 제외한다. */
    Optional<Project> findByPublicId(UUID publicId);

    /** 소프트 삭제된 프로젝트는 제외한다. */
    Optional<Project> findById(Long id);
}
