package com.fundit.project.infrastructure.persistence.aifundingstory;

import com.fundit.project.domain.aifundingstory.FundingStorySession;
import com.fundit.project.domain.aifundingstory.FundingStorySessionStatus;
import org.springframework.stereotype.Component;

@Component
class FundingStorySessionMapper {

    FundingStorySession toDomain(FundingStorySessionJpaEntity entity) {
        return FundingStorySession.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .sellerId(entity.getSellerId())
                .productDescription(entity.getProductDescription())
                .productImageUrls(entity.getProductImageUrls())
                .answers(entity.getAnswers())
                .status(FundingStorySessionStatus.valueOf(entity.getStatus()))
                .additionalQuestions(entity.getAdditionalQuestions())
                .result(entity.getResult())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    FundingStorySessionJpaEntity toEntity(FundingStorySession domain) {
        return FundingStorySessionJpaEntity.builder()
                .id(domain.getId())
                .projectId(domain.getProjectId())
                .sellerId(domain.getSellerId())
                .productDescription(domain.getProductDescription())
                .productImageUrls(domain.getProductImageUrls())
                .answers(domain.getAnswers())
                .status(domain.getStatus().name())
                .additionalQuestions(domain.getAdditionalQuestions())
                .result(domain.getResult())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
