package com.fundit.project.infrastructure.persistence.project;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectPersistenceAdapter implements ProjectRepository {

    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

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

    @Override
    public List<Project> findBySeller(UUID sellerId, List<ProjectStatus> statuses, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, LATEST_FIRST);
        var result = (statuses == null || statuses.isEmpty())
                ? jpaRepository.findBySellerIdAndDeletedAtIsNull(sellerId, pageRequest)
                : jpaRepository.findBySellerIdAndStatusInAndDeletedAtIsNull(sellerId, statuses, pageRequest);
        return result.map(mapper::toDomain).getContent();
    }

    @Override
    public long countBySeller(UUID sellerId, List<ProjectStatus> statuses) {
        PageRequest pageRequest = PageRequest.of(0, 1);
        return (statuses == null || statuses.isEmpty())
                ? jpaRepository.findBySellerIdAndDeletedAtIsNull(sellerId, pageRequest).getTotalElements()
                : jpaRepository.findBySellerIdAndStatusInAndDeletedAtIsNull(sellerId, statuses, pageRequest)
                        .getTotalElements();
    }
}
