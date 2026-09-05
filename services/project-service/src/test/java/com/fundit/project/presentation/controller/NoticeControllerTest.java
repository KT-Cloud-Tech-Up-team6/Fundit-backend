package com.fundit.project.presentation.controller;

import com.fundit.project.application.notice.NoticeService;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeCommentJpaEntity;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeJpaEntity;
import com.fundit.project.infrastructure.security.CurrentAdminArgumentResolver;
import com.fundit.project.infrastructure.security.CurrentMemberArgumentResolver;
import com.fundit.project.infrastructure.security.WebConfig;
import com.fundit.project.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NoticeController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, CurrentAdminArgumentResolver.class, WebConfig.class})
class NoticeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoticeService noticeService;

    @Test
    void 새소식을_등록하면_201을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectNoticeJpaEntity notice = ProjectNoticeJpaEntity.builder()
                .id(1L).projectId(1L).noticeType("FAQ").title("제목").content("내용").createdAt(Instant.now()).build();
        when(noticeService.create(eq(sellerId), eq(projectId), eq("FAQ"), eq("제목"), eq("내용"))).thenReturn(notice);

        // when & then
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/notices")
                        .header("X-Account-Id", sellerId.toString())
                        .contentType("application/json")
                        .content("{\"noticeType\":\"FAQ\",\"title\":\"제목\",\"content\":\"내용\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.noticeType").value("FAQ"));
    }

    @Test
    void 새소식_목록을_조회한다() throws Exception {
        // given
        UUID projectId = UUID.randomUUID();
        ProjectNoticeJpaEntity notice = ProjectNoticeJpaEntity.builder()
                .id(1L).projectId(1L).noticeType("FAQ").title("제목").content("내용").createdAt(Instant.now()).build();
        when(noticeService.list(eq(projectId), any(), any())).thenReturn(new PageImpl<>(List.of(notice)));

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("제목"));
    }

    @Test
    void 댓글을_등록하면_201을_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        ProjectNoticeCommentJpaEntity comment = ProjectNoticeCommentJpaEntity.builder()
                .id(1L).noticeId(1L).memberId(memberId).content("기대돼요!").createdAt(Instant.now()).build();
        when(noticeService.createComment(eq(memberId), eq(1L), eq("기대돼요!"))).thenReturn(comment);

        // when & then
        mockMvc.perform(post("/api/v1/notices/1/comments")
                        .header("X-Account-Id", memberId.toString())
                        .contentType("application/json")
                        .content("{\"content\":\"기대돼요!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("기대돼요!"));
    }

    @Test
    void 댓글_목록을_조회한다() throws Exception {
        // given
        ProjectNoticeCommentJpaEntity comment = ProjectNoticeCommentJpaEntity.builder()
                .id(1L).noticeId(1L).memberId(UUID.randomUUID()).content("기대돼요!").createdAt(Instant.now()).build();
        when(noticeService.listComments(eq(1L), any())).thenReturn(new PageImpl<>(List.of(comment)));

        // when & then
        mockMvc.perform(get("/api/v1/notices/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("기대돼요!"));
    }
}
