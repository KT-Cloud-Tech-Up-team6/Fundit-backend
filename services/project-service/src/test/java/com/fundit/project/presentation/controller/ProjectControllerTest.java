package com.fundit.project.presentation.controller;

import com.fundit.project.application.project.ProjectQueryService;
import com.fundit.project.application.project.ProjectService;
import com.fundit.project.application.project.ProjectStatsService;
import com.fundit.project.domain.project.BusinessType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.project.query.ProjectListProjection;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, CurrentAdminArgumentResolver.class, WebConfig.class})
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;
    @MockitoBean
    private ProjectQueryService projectQueryService;
    @MockitoBean
    private ProjectStatsService projectStatsService;

    private Project draftProject(UUID sellerId, UUID publicId) {
        return Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(ProjectStatus.DRAFT)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void 목록조회는_200과_페이지네이션_형태를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        ProjectListProjection projection = mock(ProjectListProjection.class);
        when(projection.getProjectId()).thenReturn(UUID.randomUUID());
        when(projection.getTitle()).thenReturn("프로젝트A");
        when(projection.getStatus()).thenReturn("DRAFT");
        when(projectService.list(eq(sellerId), isNull(), any())).thenReturn(new PageImpl<>(List.of(projection)));

        // when & then
        mockMvc.perform(get("/api/v1/projects").header("X-Account-Id", sellerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("프로젝트A"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void 생성하면_201과_DRAFT_상태를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        when(projectService.create(sellerId)).thenReturn(draftProject(sellerId, publicId));

        // when & then
        mockMvc.perform(post("/api/v1/projects").header("X-Account-Id", sellerId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(publicId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void 삭제하면_204를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/v1/projects/" + publicId).header("X-Account-Id", sellerId.toString()))
                .andExpect(status().isNoContent());
        verify(projectService).delete(sellerId, publicId);
    }

    @Test
    void 기본정보를_수정하면_200과_변경된_값을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project updated = draftProject(sellerId, publicId).toBuilder()
                .businessType(BusinessType.SOLE).categoryMajor("테크·가전").categoryMinor("생활가전")
                .title("제목").goalAmount(1_000_000L).build();
        when(projectService.updateBasicInfo(eq(sellerId), eq(publicId), any())).thenReturn(updated);

        // when & then
        mockMvc.perform(patch("/api/v1/projects/" + publicId + "/basic-info")
                        .header("X-Account-Id", sellerId.toString())
                        .contentType("application/json")
                        .content("""
                                {"businessType":"SOLE","categoryMajor":"테크·가전","categoryMinor":"생활가전","title":"제목","goalAmount":1000000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.goalAmount").value(1_000_000));
    }

    @Test
    void 개인정보_동의_처리는_200과_동의시각을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        when(projectService.consentPrivacy(sellerId, publicId, true)).thenReturn(Instant.parse("2026-09-05T00:00:00Z"));

        // when & then
        mockMvc.perform(post("/api/v1/projects/" + publicId + "/privacy-consent")
                        .header("X-Account-Id", sellerId.toString())
                        .contentType("application/json")
                        .content("{\"agreed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(publicId.toString()));
    }

    @Test
    void 심사제출하면_200과_PENDING_REVIEW_상태를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project submitted = draftProject(sellerId, publicId).toBuilder().status(ProjectStatus.PENDING_REVIEW).build();
        when(projectService.submit(sellerId, publicId)).thenReturn(submitted);

        // when & then
        mockMvc.perform(post("/api/v1/projects/" + publicId + "/submit").header("X-Account-Id", sellerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    void 소개콘텐츠를_수정하면_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        when(projectService.updateStory(eq(sellerId), eq(publicId), any())).thenReturn(draftProject(sellerId, publicId));

        // when & then
        mockMvc.perform(patch("/api/v1/projects/" + publicId + "/story")
                        .header("X-Account-Id", sellerId.toString())
                        .contentType("application/json")
                        .content("""
                                {"title":"제목","coverImageUrl":"http://img","introContent":[{"type":"TEXT","value":"본문"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(publicId.toString()));
    }

    @Test
    void 미리보기_조회는_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        var view = new ProjectQueryService.ProjectDetailView(publicId, "제목", "DRAFT", 1_000_000L,
                new ProjectQueryService.FundingStatusView(0, 0, 0, null), false,
                new ProjectQueryService.SellerView(sellerId, null));
        when(projectQueryService.getPreview(sellerId, publicId)).thenReturn(view);

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + publicId + "/preview").header("X-Account-Id", sellerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("제목"));
    }

    @Test
    void 공개_상세조회는_인증없이_200을_반환한다() throws Exception {
        // given
        UUID publicId = UUID.randomUUID();
        var view = new ProjectQueryService.ProjectDetailView(publicId, "제목", "ONGOING", 1_000_000L,
                new ProjectQueryService.FundingStatusView(320000, 64, 128, 5L), true,
                new ProjectQueryService.SellerView(UUID.randomUUID(), null));
        when(projectQueryService.getPublicDetail(publicId)).thenReturn(view);

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONGOING"))
                .andExpect(jsonPath("$.hasLiveVerification").value(true));
    }

    @Test
    void 환불정책_조회는_인증없이_200을_반환한다() throws Exception {
        // given
        UUID publicId = UUID.randomUUID();
        var view = new ProjectQueryService.RefundPolicyView(
                new ProjectQueryService.CommonPolicyView("펀딩 마감 전까지", true),
                List.of(new ProjectQueryService.RewardPolicyView(1L, false)));
        when(projectQueryService.getRefundPolicy(publicId)).thenReturn(view);

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + publicId + "/refund-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commonPolicy.goalFailedAutoRefund").value(true))
                .andExpect(jsonPath("$.rewardPolicies[0].rewardId").value(1));
    }

    @Test
    void 펀딩현황_조회는_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        var view = new ProjectStatsService.FundingStatusView(320000, 64, 128, 40, 210, List.of(), 5L, null);
        when(projectStatsService.getFundingStatus(sellerId, publicId)).thenReturn(view);

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + publicId + "/funding-status").header("X-Account-Id", sellerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentAmount").value(320000));
    }

    @Test
    void 찜통계_조회는_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        when(projectStatsService.getWishStats(sellerId, publicId)).thenReturn(new ProjectStatsService.WishStatsView(210, 40));

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + publicId + "/wish-stats").header("X-Account-Id", sellerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wishCount").value(210));
    }
}
