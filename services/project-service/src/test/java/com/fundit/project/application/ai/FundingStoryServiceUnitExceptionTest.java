package com.fundit.project.application.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.media.MediaUrlValidator;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.aifundingstory.FundingStoryResult;
import com.fundit.project.domain.aifundingstory.FundingStorySection;
import com.fundit.project.domain.aifundingstory.FundingStorySession;
import com.fundit.project.domain.aifundingstory.FundingStorySessionRepository;
import com.fundit.project.domain.aifundingstory.FundingStorySessionStatus;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingStoryServiceUnitExceptionTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private FundingStorySessionRepository sessionRepository;
    @Mock
    private FundingStoryAiClient fundingStoryAiClient;
    @Mock
    private MediaUrlValidator mediaUrlValidator;

    @InjectMocks
    private FundingStoryService fundingStoryService;

    @Test
    void 타인_세션을_조회하면_403_예외가_발생한다() {
        // given
        UUID sessionId = UUID.randomUUID();
        FundingStorySession session = FundingStorySession.builder()
                .id(sessionId).projectId(1L).sellerId(UUID.randomUUID()).productDescription("설명")
                .status(FundingStorySessionStatus.COMPLETED).build();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // when & then
        assertThatThrownBy(() -> fundingStoryService.getSession(UUID.randomUUID(), sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    @Test
    void 생성이_완료되지_않은_세션을_반영하려하면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        FundingStorySession session = FundingStorySession.builder()
                .id(sessionId).projectId(1L).sellerId(sellerId).productDescription("설명")
                .status(FundingStorySessionStatus.GENERATING).build();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // when & then
        assertThatThrownBy(() -> fundingStoryService.applyToProject(sellerId, sessionId, "OVERWRITE", Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void 세션_생성시_검증되지_않은_이미지_URL이면_예외가_전파된다() {
        // given — 업로드 주소 발급을 거치지 않은(또는 S3에 없는) URL
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        String imageUrl = "https://attacker.example.com/a.jpg";
        Project project = Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(ProjectStatus.DRAFT)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        doThrow(new BusinessException(ProjectErrorCode.INVALID_MEDIA_URL))
                .when(mediaUrlValidator).validate(eq(publicId), eq(imageUrl), any());

        // when & then
        assertThatThrownBy(() -> fundingStoryService.createSession(
                sellerId, publicId, "제품설명", List.of(imageUrl), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_MEDIA_URL);
    }

    @Test
    void 반영시_이미지_블록_검증에_실패하면_예외가_전파된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        String imageUrl = "https://attacker.example.com/generated.jpg";
        FundingStoryResult result = new FundingStoryResult(
                List.of(new FundingStorySection("INTRO", "제목", "새 본문", List.of(imageUrl))), List.of(), List.of());
        FundingStorySession session = FundingStorySession.builder()
                .id(sessionId).projectId(1L).sellerId(sellerId).productDescription("설명")
                .status(FundingStorySessionStatus.COMPLETED).result(result).build();
        Project project = Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(ProjectStatus.DRAFT)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        doThrow(new BusinessException(ProjectErrorCode.MEDIA_TOO_LARGE))
                .when(mediaUrlValidator).validate(eq(publicId), eq(imageUrl), any());

        // when & then
        assertThatThrownBy(() -> fundingStoryService.applyToProject(sellerId, sessionId, "OVERWRITE", Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.MEDIA_TOO_LARGE);
    }
}
