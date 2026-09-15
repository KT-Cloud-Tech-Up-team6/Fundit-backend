package com.fundit.project.application.ai;

import com.fundit.project.application.media.MediaCategory;
import com.fundit.project.application.media.MediaUrlValidator;
import com.fundit.project.domain.aifundingstory.FundingStoryImageSource;
import com.fundit.project.domain.aifundingstory.FundingStoryResult;
import com.fundit.project.domain.aifundingstory.FundingStorySection;
import com.fundit.project.domain.aifundingstory.FundingStorySession;
import com.fundit.project.domain.aifundingstory.FundingStorySessionRepository;
import com.fundit.project.domain.aifundingstory.FundingStorySessionStatus;
import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.IntroContentType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingStoryServiceUnitTest {

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

    private Project ownedProject(UUID sellerId, UUID publicId) {
        return Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(ProjectStatus.DRAFT)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void 세션_생성시_목_생성기가_즉시_완료처리한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project project = ownedProject(sellerId, publicId);
        FundingStoryResult result = new FundingStoryResult(
                List.of(new FundingStorySection("INTRO", "제목", "본문", List.of())), List.of(), List.of());
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(fundingStoryAiClient.generate(any(), any(), any())).thenReturn(result);

        // when
        FundingStorySession session = fundingStoryService.createSession(sellerId, publicId, "제품설명", null, null);

        // then
        assertThat(session.getStatus()).isEqualTo(FundingStorySessionStatus.COMPLETED);
        assertThat(session.getResult().sections()).hasSize(1);
    }

    @Test
    void 세션_생성시_전달된_이미지_URL을_전부_검증한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project project = ownedProject(sellerId, publicId);
        List<String> imageUrls = List.of("https://cdn/projects/" + publicId + "/a.jpg",
                "https://cdn/projects/" + publicId + "/b.jpg");
        FundingStoryResult result = new FundingStoryResult(List.of(), List.of(), List.of());
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(fundingStoryAiClient.generate(any(), any(), any())).thenReturn(result);

        // when
        fundingStoryService.createSession(sellerId, publicId, "제품설명", imageUrls, null);

        // then
        for (String imageUrl : imageUrls) {
            verify(mediaUrlValidator).validate(publicId, imageUrl, MediaCategory.IMAGE);
        }
    }

    @Test
    void 본인_세션을_조회한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        FundingStorySession session = FundingStorySession.builder()
                .id(sessionId).projectId(1L).sellerId(sellerId).productDescription("설명")
                .status(FundingStorySessionStatus.COMPLETED).build();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // when
        FundingStorySession result = fundingStoryService.getSession(sellerId, sessionId);

        // then
        assertThat(result.getId()).isEqualTo(sessionId);
    }

    @Test
    void OVERWRITE_모드는_기존_소개콘텐츠를_대체한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        FundingStoryResult result = new FundingStoryResult(
                List.of(new FundingStorySection("INTRO", "제목", "새 본문", List.of())),
                List.of(new FundingStoryImageSource("http://img", "UPLOADED")), List.of());
        FundingStorySession session = FundingStorySession.builder()
                .id(sessionId).projectId(1L).sellerId(sellerId).productDescription("설명")
                .status(FundingStorySessionStatus.COMPLETED).result(result).build();
        Project project = ownedProject(sellerId, UUID.randomUUID()).toBuilder()
                .introContent(List.of(new IntroContentBlock(IntroContentType.TEXT, "기존 본문")))
                .build();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        Project applied = fundingStoryService.applyToProject(sellerId, sessionId, "OVERWRITE", Map.of());

        // then
        assertThat(applied.getIntroContent()).extracting(IntroContentBlock::value).containsExactly("새 본문");
    }

    @Test
    void COPY_모드는_기존_소개콘텐츠_뒤에_추가한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        FundingStoryResult result = new FundingStoryResult(
                List.of(new FundingStorySection("INTRO", "제목", "새 본문", List.of())), List.of(), List.of());
        FundingStorySession session = FundingStorySession.builder()
                .id(sessionId).projectId(1L).sellerId(sellerId).productDescription("설명")
                .status(FundingStorySessionStatus.COMPLETED).result(result).build();
        Project project = ownedProject(sellerId, UUID.randomUUID()).toBuilder()
                .introContent(List.of(new IntroContentBlock(IntroContentType.TEXT, "기존 본문")))
                .build();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        Project applied = fundingStoryService.applyToProject(sellerId, sessionId, "COPY", Map.of());

        // then
        assertThat(applied.getIntroContent()).extracting(IntroContentBlock::value).containsExactly("기존 본문", "새 본문");
    }

    @Test
    void 반영시_IMAGE_블록만_미디어_URL_검증을_거친다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        String imageUrl = "https://cdn/projects/" + publicId + "/generated.jpg";
        FundingStoryResult result = new FundingStoryResult(
                List.of(new FundingStorySection("INTRO", "제목", "새 본문", List.of(imageUrl))), List.of(), List.of());
        FundingStorySession session = FundingStorySession.builder()
                .id(sessionId).projectId(1L).sellerId(sellerId).productDescription("설명")
                .status(FundingStorySessionStatus.COMPLETED).result(result).build();
        Project project = ownedProject(sellerId, publicId);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        fundingStoryService.applyToProject(sellerId, sessionId, "OVERWRITE", Map.of());

        // then — TEXT 블록("새 본문")은 검증 대상이 아니고, IMAGE 블록만 검증한다
        verify(mediaUrlValidator).validate(publicId, imageUrl, MediaCategory.IMAGE);
        verify(mediaUrlValidator, never()).validate(publicId, "새 본문", MediaCategory.IMAGE);
    }
}
