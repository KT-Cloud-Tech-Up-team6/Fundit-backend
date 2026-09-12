package com.fundit.project.application.media;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaUploadServiceUnitExceptionTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private MediaStorageClient storageClient;

    private MediaUploadService service(long ttl) {
        return new MediaUploadService(projectRepository, storageClient, ttl);
    }

    private Project ownedProject(UUID sellerId, UUID publicId) {
        return Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(ProjectStatus.DRAFT)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void 존재하지_않는_프로젝트면_404_예외가_발생한다() {
        // given
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service(5).issueUploadUrl(
                UUID.randomUUID(), projectId, "a.jpg", "image/jpeg", 1024L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 타인_소유_프로젝트면_403_예외가_발생한다() {
        // given
        UUID projectId = UUID.randomUUID();
        Project project = ownedProject(UUID.randomUUID(), projectId);
        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> service(5).issueUploadUrl(
                UUID.randomUUID(), projectId, "a.jpg", "image/jpeg", 1024L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    @Test
    void 화이트리스트에_없는_contentType이면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = ownedProject(sellerId, projectId);
        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> service(5).issueUploadUrl(
                sellerId, projectId, "a.exe", "application/octet-stream", 1024L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void 확장자가_contentType과_다른_카테고리면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = ownedProject(sellerId, projectId);
        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> service(5).issueUploadUrl(
                sellerId, projectId, "a.mp4", "image/jpeg", 1024L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void 확장자가_없는_파일명이면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = ownedProject(sellerId, projectId);
        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> service(5).issueUploadUrl(
                sellerId, projectId, "noext", "image/jpeg", 1024L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void 이미지_용량이_10MB를_초과하면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = ownedProject(sellerId, projectId);
        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> service(5).issueUploadUrl(
                sellerId, projectId, "a.jpg", "image/jpeg", 10L * 1024 * 1024 + 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.MEDIA_TOO_LARGE);
    }

    @Test
    void 용량이_0이하면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = ownedProject(sellerId, projectId);
        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> service(5).issueUploadUrl(
                sellerId, projectId, "a.jpg", "image/jpeg", 0L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.MEDIA_TOO_LARGE);
    }
}
