package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 내부 전용 조회 — fulfillment-service가 판매자 소유권 검증에 쓰는 최소 필드(sellerId)만 제공한다. */
@Service
@RequiredArgsConstructor
public class ProjectInternalQueryService {

    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public ProjectSnapshot getSnapshot(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return new ProjectSnapshot(project.getSellerId(), project.getPublicId());
    }

    public record ProjectSnapshot(UUID sellerId, UUID publicId) {
    }
}
