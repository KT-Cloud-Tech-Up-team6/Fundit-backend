package com.fundit.project.infrastructure.persistence.project;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectPersistenceAdapter implements ProjectRepository {

    private final ProjectJpaRepository jpaRepository;
    private final ProjectMapper mapper;

    @Override
    public Project save(Project project) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(project)));
    }

    @Override
    public Optional<Project> findByPublicId(UUID publicId) {
        return jpaRepository.findByPublicIdAndDeletedAtIsNull(publicId).map(mapper::toDomain);
    }

    @Override
    public Optional<Project> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(mapper::toDomain);
    }
}
