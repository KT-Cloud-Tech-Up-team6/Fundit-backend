package com.fundit.project.infrastructure.persistence.pagesummary;

import com.fundit.project.domain.pagesummary.PageSummary;
import com.fundit.project.domain.pagesummary.PageSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PageSummaryPersistenceAdapter implements PageSummaryRepository {

    private final PageSummaryJpaRepository jpaRepository;
    private final PageSummaryMapper mapper;

    @Override
    public Optional<PageSummary> findByProjectId(Long projectId) {
        return jpaRepository.findById(projectId).map(mapper::toDomain);
    }

    @Override
    public PageSummary save(PageSummary summary) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(summary)));
    }

    @Override
    public void markDirty(Long projectId, Instant now) {
        jpaRepository.markDirty(projectId, now);
    }

    @Override
    public List<Long> findDueProjectIds(Instant now, int limit) {
        return jpaRepository.findDueProjectIds(now, PageRequest.of(0, limit));
    }
}
