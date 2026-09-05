package com.fundit.project.application.community;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.community.CommunityAnswerJpaRepository;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaEntity;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityServiceUnitExceptionTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private CommunityPostJpaRepository postJpaRepository;
    @Mock
    private CommunityAnswerJpaRepository answerJpaRepository;

    @InjectMocks
    private CommunityService communityService;

    @Test
    void 존재하지_않는_프로젝트에_게시글_등록시_404_예외가_발생한다() {
        // given
        UUID publicId = UUID.randomUUID();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> communityService.createPost(UUID.randomUUID(), publicId, "QUESTION", "질문"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 비공개_프로젝트를_타인이_조회하면_404_예외가_발생한다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project project = Project.builder()
                .id(1L).publicId(publicId).sellerId(UUID.randomUUID()).status(ProjectStatus.DRAFT)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> communityService.listPosts(publicId, null, false, UUID.randomUUID(), PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 존재하지_않는_게시글에_답변등록시_404_예외가_발생한다() {
        // given
        when(postJpaRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> communityService.upsertAnswer(UUID.randomUUID(), 99L, "답변"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 타인_소유_게시글에_답변등록시_403_예외가_발생한다() {
        // given
        CommunityPostJpaEntity post = CommunityPostJpaEntity.builder()
                .id(7001L).projectId(1L).memberId(UUID.randomUUID()).postType("QUESTION")
                .content("질문").createdAt(Instant.now()).build();
        Project project = Project.builder()
                .id(1L).publicId(UUID.randomUUID()).sellerId(UUID.randomUUID()).status(ProjectStatus.ONGOING)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(postJpaRepository.findById(7001L)).thenReturn(Optional.of(post));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> communityService.upsertAnswer(UUID.randomUUID(), 7001L, "답변"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }
}
