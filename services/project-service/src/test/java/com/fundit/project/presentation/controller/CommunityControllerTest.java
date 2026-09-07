package com.fundit.project.presentation.controller;

import com.fundit.project.application.community.CommunityService;
import com.fundit.project.infrastructure.persistence.community.CommunityAnswerJpaEntity;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaEntity;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommunityController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, CurrentAdminArgumentResolver.class, WebConfig.class})
class CommunityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommunityService communityService;

    @Test
    void 질문_응원_게시글을_등록하면_201을_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        CommunityPostJpaEntity post = CommunityPostJpaEntity.builder()
                .id(7001L).projectId(1L).memberId(memberId).postType("QUESTION").content("질문").createdAt(Instant.now()).build();
        when(communityService.createPost(eq(memberId), eq(projectId), eq("QUESTION"), eq("질문"))).thenReturn(post);

        // when & then
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/community/posts")
                        .header("X-Account-Id", memberId.toString())
                        .contentType("application/json")
                        .content("{\"postType\":\"QUESTION\",\"content\":\"질문\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postType").value("QUESTION"));
    }

    @Test
    void 게시글_목록을_비로그인으로도_조회할_수_있다() throws Exception {
        // given
        UUID projectId = UUID.randomUUID();
        var view = new CommunityService.CommunityPostView(7001L, "QUESTION", "질문", Instant.now(), Optional.empty());
        when(communityService.listPosts(eq(projectId), any(), anyBoolean(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(view)));

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/community/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("질문"));
    }

    @Test
    void 답변을_등록하면_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        CommunityAnswerJpaEntity answer = CommunityAnswerJpaEntity.builder()
                .id(1L).postId(7001L).sellerId(sellerId).content("답변").build();
        when(communityService.upsertAnswer(eq(sellerId), eq(7001L), eq("답변"))).thenReturn(answer);

        // when & then
        mockMvc.perform(post("/api/v1/community/posts/7001/answer")
                        .header("X-Account-Id", sellerId.toString())
                        .contentType("application/json")
                        .content("{\"content\":\"답변\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer.content").value("답변"));
    }
}
