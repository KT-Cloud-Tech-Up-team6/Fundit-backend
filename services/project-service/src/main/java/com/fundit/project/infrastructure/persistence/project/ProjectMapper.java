package com.fundit.project.infrastructure.persistence.project;

import com.fundit.project.domain.project.BusinessType;
import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.IntroContentType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class ProjectMapper {

    Project toDomain(ProjectJpaEntity entity) {
        return Project.builder()
                .id(entity.getId())
                .publicId(entity.getPublicId())
                .sellerId(entity.getSellerId())
                .businessType(entity.getBusinessType() == null ? null : BusinessType.valueOf(entity.getBusinessType()))
                .categoryMajor(entity.getCategoryMajor())
                .categoryMinor(entity.getCategoryMinor())
                .title(entity.getTitle())
                .goalAmount(entity.getGoalAmount())
                .fundingStartAt(entity.getFundingStartAt())
                .fundingDeadline(entity.getFundingDeadline())
                .status(ProjectStatus.valueOf(entity.getStatus()))
                .coverImageUrl(entity.getCoverImageUrl())
                .introContent(toDomainIntroContent(entity.getIntroContent()))
                .projectDisplayCode(entity.getProjectDisplayCode())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    ProjectJpaEntity toEntity(Project domain) {
        return ProjectJpaEntity.builder()
                .id(domain.getId())
                .publicId(domain.getPublicId())
                .sellerId(domain.getSellerId())
                .businessType(domain.getBusinessType() == null ? null : domain.getBusinessType().name())
                .categoryMajor(domain.getCategoryMajor())
                .categoryMinor(domain.getCategoryMinor())
                .title(domain.getTitle())
                .goalAmount(domain.getGoalAmount())
                .fundingStartAt(domain.getFundingStartAt())
                .fundingDeadline(domain.getFundingDeadline())
                .status(domain.getStatus().name())
                .coverImageUrl(domain.getCoverImageUrl())
                .introContent(toEntityIntroContent(domain.getIntroContent()))
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .deletedAt(domain.getDeletedAt())
                .build();
    }

    private List<IntroContentBlock> toDomainIntroContent(List<IntroContentBlockEntity> entity) {
        if (entity == null) return null;
        return entity.stream()
                .map(e -> new IntroContentBlock(IntroContentType.valueOf(e.type()), e.value()))
                .toList();
    }

    private List<IntroContentBlockEntity> toEntityIntroContent(List<IntroContentBlock> domain) {
        if (domain == null) return null;
        return domain.stream()
                .map(b -> new IntroContentBlockEntity(b.type().name(), b.value()))
                .toList();
    }
}
