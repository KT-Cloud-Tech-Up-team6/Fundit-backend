package com.fundit.project.domain.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {

    Project save(Project project);

    Optional<Project> findByPublicId(UUID publicId);

    Optional<Project> findById(Long id);

    List<Project> findBySeller(UUID sellerId, List<ProjectStatus> statuses, int page, int size);

    long countBySeller(UUID sellerId, List<ProjectStatus> statuses);
}
