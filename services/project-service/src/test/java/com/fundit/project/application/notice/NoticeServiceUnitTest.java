package com.fundit.project.application.notice;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeCommentJpaEntity;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeCommentJpaRepository;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeJpaEntity;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeJpaRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectNoticeJpaRepository noticeJpaRepository;
    @Mock
    private ProjectNoticeCommentJpaRepository commentJpaRepository;

    @InjectMocks
    private NoticeService noticeService;

    private Project publicProject(UUID sellerId, UUID publicId) {
        return Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(ProjectStatus.ONGOING)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void 본인_프로젝트에_새소식을_등록한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project project = publicProject(sellerId, publicId);
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(noticeJpaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        ProjectNoticeJpaEntity notice = noticeService.create(sellerId, publicId, "FAQ", "제목", "내용");

        // then
        assertThat(notice.getNoticeType()).isEqualTo("FAQ");
        assertThat(notice.getTitle()).isEqualTo("제목");
    }

    @Test
    void 공개_프로젝트의_새소식_목록을_조회한다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project project = publicProject(UUID.randomUUID(), publicId);
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(noticeJpaRepository.findList(anyLong(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        // when
        var result = noticeService.list(publicId, null, PageRequest.of(0, 20));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void 댓글을_등록한다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(noticeJpaRepository.existsById(1L)).thenReturn(true);
        when(commentJpaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        ProjectNoticeCommentJpaEntity comment = noticeService.createComment(memberId, 1L, "기대돼요!");

        // then
        assertThat(comment.getContent()).isEqualTo("기대돼요!");
        assertThat(comment.getMemberId()).isEqualTo(memberId);
    }

    @Test
    void 댓글_목록을_조회한다() {
        // given
        when(noticeJpaRepository.existsById(1L)).thenReturn(true);
        when(commentJpaRepository.findByNoticeIdAndDeletedAtIsNull(anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        // when
        var result = noticeService.listComments(1L, PageRequest.of(0, 20));

        // then
        assertThat(result).isEmpty();
    }
}
