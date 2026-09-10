package com.fundit.project.application.media;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaUploadServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private MediaStorageClient storageClient;

    private MediaUploadService mediaUploadService;

    private Project ownedProject(UUID sellerId, UUID publicId) {
        return Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(ProjectStatus.DRAFT)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private void setUpWithTtl(long ttlMinutes) {
        mediaUploadService = new MediaUploadService(projectRepository, storageClient, ttlMinutes);
    }

    @Test
    void 본인_소유_프로젝트면_이미지_업로드_주소를_발급한다() {
        // given
        setUpWithTtl(5);
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = ownedProject(sellerId, projectId);
        when(projectRepository.findByPublicId(projectId)).thenReturn(Optional.of(project));
        when(storageClient.presignPut(anyString(), eq("image/png"), eq(Duration.ofMinutes(5))))
                .thenReturn(new MediaStorageClient.PresignedUpload("https://upload", "https://file"));

        // when
        MediaStorageClient.PresignedUpload result = mediaUploadService.issueUploadUrl(
                sellerId, projectId, "cover.png", "image/png", 1024L);

        // then
        assertThat(result.uploadUrl()).isEqualTo("https://upload");
        assertThat(result.fileUrl()).isEqualTo("https://file");
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageClient).presignPut(keyCaptor.capture(), eq("image/png"), any());
        assertThat(keyCaptor.getValue()).startsWith("projects/" + projectId + "/").endsWith(".png");
    }
}
