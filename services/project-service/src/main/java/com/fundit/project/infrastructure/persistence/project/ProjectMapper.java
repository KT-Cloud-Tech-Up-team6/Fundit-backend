package com.fundit.project.infrastructure.persistence.project;

import com.fundit.project.domain.project.Project;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {

    Project toDomain(ProjectJpaEntity entity) {
        return Project.builder()
                .id(entity.getId())
                .publicId(entity.getPublicId())
                .sellerId(entity.getSellerId())
                .categoryMajor(entity.getCategoryMajor())
                .categoryMinor(entity.getCategoryMinor())
                .businessType(entity.getBusinessType())
                .privacyAgreed(Boolean.TRUE.equals(entity.getPrivacyAgreed()))
                .title(entity.getTitle())
                .thumbnailImageUrl(entity.getThumbnailImageUrl())
                .introContent(entity.getIntroContent())
                .goalAmount(entity.getGoalAmount())
                .fundingStartAt(entity.getFundingStartAt())
                .fundingDeadline(entity.getFundingDeadline())
                .status(entity.getStatus())
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
                .categoryMajor(domain.getCategoryMajor())
                .categoryMinor(domain.getCategoryMinor())
                .businessType(domain.getBusinessType())
                .privacyAgreed(domain.isPrivacyAgreed())
                .title(domain.getTitle())
                .thumbnailImageUrl(domain.getThumbnailImageUrl())
                .introContent(domain.getIntroContent())
                .goalAmount(domain.getGoalAmount())
                .fundingStartAt(domain.getFundingStartAt())
                .fundingDeadline(domain.getFundingDeadline())
                .status(domain.getStatus())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .deletedAt(domain.getDeletedAt())
                .build();
    }
}
