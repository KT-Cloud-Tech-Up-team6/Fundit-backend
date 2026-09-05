package com.fundit.project.application.community;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.community.CommunityAnswerJpaEntity;
import com.fundit.project.infrastructure.persistence.community.CommunityAnswerJpaRepository;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaEntity;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private CommunityPostJpaRepository postJpaRepository;
    @Mock
    private CommunityAnswerJpaRepository answerJpaRepository;

    @InjectMocks
    private CommunityService communityService;

    private Project publicProject(UUID sellerId, UUID publicId) {
        return Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(ProjectStatus.ONGOING)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void 질문_응원_게시글을_등록한다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project project = publicProject(UUID.randomUUID(), publicId);
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(postJpaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        CommunityPostJpaEntity post = communityService.createPost(memberId, publicId, "QUESTION", "언제 배송되나요?");

        // then
        assertThat(post.getPostType()).isEqualTo("QUESTION");
        assertThat(post.getMemberId()).isEqualTo(memberId);
    }

    @Nested
    class 목록조회 {

        @Test
        void 답변이_있으면_답변정보를_함께_반환한다() {
            // given
            UUID publicId = UUID.randomUUID();
            Project project = publicProject(UUID.randomUUID(), publicId);
            CommunityPostJpaEntity post = CommunityPostJpaEntity.builder()
                    .id(7001L).projectId(1L).memberId(UUID.randomUUID()).postType("QUESTION")
                    .content("질문").createdAt(Instant.now()).build();
            CommunityAnswerJpaEntity answer = CommunityAnswerJpaEntity.builder()
                    .id(1L).postId(7001L).sellerId(project.getSellerId()).content("답변").build();
            when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
            when(postJpaRepository.findList(anyLong(), any(), anyBoolean(), any()))
                    .thenReturn(new PageImpl<>(List.of(post)));
            when(answerJpaRepository.findByPostIdIn(List.of(7001L))).thenReturn(List.of(answer));

            // when
            var result = communityService.listPosts(publicId, null, false, null, PageRequest.of(0, 20));

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).answer()).isPresent();
        }

        @Test
        void answeredOnly는_본인_소유일때만_적용된다() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID publicId = UUID.randomUUID();
            Project project = publicProject(sellerId, publicId);
            when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
            when(postJpaRepository.findList(anyLong(), any(), anyBoolean(), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            // when
            communityService.listPosts(publicId, null, true, sellerId, PageRequest.of(0, 20));

            // then
            org.mockito.Mockito.verify(postJpaRepository).findList(1L, null, true, PageRequest.of(0, 20));
        }
    }

    @Nested
    class 답변_등록 {

        @Test
        void 답변이_없으면_새로_생성한다() {
            // given
            UUID sellerId = UUID.randomUUID();
            CommunityPostJpaEntity post = CommunityPostJpaEntity.builder()
                    .id(7001L).projectId(1L).memberId(UUID.randomUUID()).postType("QUESTION")
                    .content("질문").createdAt(Instant.now()).build();
            Project project = publicProject(sellerId, UUID.randomUUID());
            when(postJpaRepository.findById(7001L)).thenReturn(Optional.of(post));
            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(answerJpaRepository.findByPostId(7001L)).thenReturn(Optional.empty());
            when(answerJpaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // when
            CommunityAnswerJpaEntity result = communityService.upsertAnswer(sellerId, 7001L, "답변입니다");

            // then
            assertThat(result.getContent()).isEqualTo("답변입니다");
        }

        @Test
        void 기존_답변이_있으면_내용을_수정한다() {
            // given
            UUID sellerId = UUID.randomUUID();
            CommunityPostJpaEntity post = CommunityPostJpaEntity.builder()
                    .id(7001L).projectId(1L).memberId(UUID.randomUUID()).postType("QUESTION")
                    .content("질문").createdAt(Instant.now()).build();
            Project project = publicProject(sellerId, UUID.randomUUID());
            CommunityAnswerJpaEntity existing = CommunityAnswerJpaEntity.builder()
                    .id(1L).postId(7001L).sellerId(sellerId).content("기존답변").build();
            when(postJpaRepository.findById(7001L)).thenReturn(Optional.of(post));
            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(answerJpaRepository.findByPostId(7001L)).thenReturn(Optional.of(existing));
            when(answerJpaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // when
            CommunityAnswerJpaEntity result = communityService.upsertAnswer(sellerId, 7001L, "수정된답변");

            // then
            assertThat(result.getContent()).isEqualTo("수정된답변");
        }
    }
}
