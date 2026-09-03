package com.fundit.project.presentation.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.fundit.project.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 프로젝트 개설부터 검수 승인까지 한 번에 태워, 계층을 갈라 놓은 단위 테스트에서는
 * 드러나지 않는 상태 전이·직렬화·트랜잭션 배선을 확인한다.
 */
@AutoConfigureMockMvc
@DisplayName("프로젝트 라이프사이클 API 통합")
@Sql("/sql/insert-categories.sql")
class ProjectApiIntegrationTest extends IntegrationTestSupport {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    private UUID sellerId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        sellerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
    }

    @Test
    void 개설부터_검수_승인까지_이어진다() throws Exception {
        // given
        UUID projectId = createProject();

        // when & then — 개설 직후에는 작성중이다
        assertThat(readStatus(projectId)).isEqualTo("DRAFT");

        // when & then — 기본정보를 채워도 작성중을 유지한다
        mockMvc.perform(patch("/api/v1/projects/{projectId}/basic-info", projectId)
                        .header(USER_ID_HEADER, sellerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessType": "GENERAL",
                                  "categoryMajor": "홈·리빙",
                                  "categoryMinor": "인테리어",
                                  "goalAmount": 5000000,
                                  "privacyAgreed": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        // when & then — 리워드를 등록한다
        createReward(projectId);

        // when & then — 상세페이지를 채워 검수를 요청하면 검수중으로 넘어간다
        mockMvc.perform(patch("/api/v1/projects/{projectId}/detail", projectId)
                        .header(USER_ID_HEADER, sellerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "무선 미니 가습기",
                                  "thumbnailImageUrl": "https://cdn.example.com/p/1.jpg",
                                  "introContent": {"text": "소개 본문"},
                                  "isDraft": false
                                }
                                """))
                .andExpect(status().isOk());
        assertThat(readStatus(projectId)).isEqualTo("PENDING_REVIEW");

        // when & then — 관리자가 승인하면 진행중이 되고 펀딩 일정이 확정된다
        mockMvc.perform(patch("/api/v1/projects/{projectId}/review", projectId)
                        .header(USER_ID_HEADER, adminId)
                        .header(USER_ROLE_HEADER, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "fundingStartAt": "2026-10-01T00:00:00Z",
                                  "fundingDeadline": "2026-11-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONGOING"));
    }

    @Test
    void 진행중_프로젝트는_비로그인도_상세를_볼_수_있다() throws Exception {
        // given
        UUID projectId = createOngoingProject();

        // when & then
        mockMvc.perform(get("/api/v1/projects/{projectId}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("무선 미니 가습기"))
                .andExpect(jsonPath("$.goalAmount").value(5000000));
    }

    @Test
    void 반려되면_작성중으로_돌아가_다시_수정할_수_있다() throws Exception {
        // given
        UUID projectId = createPendingReviewProject();

        // when
        mockMvc.perform(patch("/api/v1/projects/{projectId}/review", projectId)
                        .header(USER_ID_HEADER, adminId)
                        .header(USER_ROLE_HEADER, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "REJECT", "rejectReason": "사업자 서류 미비"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        // then — 잠금이 풀려 임시저장이 다시 통과한다
        mockMvc.perform(patch("/api/v1/projects/{projectId}/detail", projectId)
                        .header(USER_ID_HEADER, sellerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "반려 후 수정한 제목", "isDraft": true}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void 판매자_목록은_본인_프로젝트만_돌려준다() throws Exception {
        // given
        createProject();
        UUID otherSeller = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/projects").header(USER_ID_HEADER, otherSeller))
                .andExpect(status().isCreated());

        // when & then
        mockMvc.perform(get("/api/v1/projects").header(USER_ID_HEADER, sellerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void 임시저장은_저장_시각을_갱신해_돌려준다() throws Exception {
        // given
        UUID projectId = createProject();

        // when
        String response = mockMvc.perform(patch("/api/v1/projects/{projectId}/detail", projectId)
                        .header(USER_ID_HEADER, sellerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "임시 제목", "isDraft": true}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then
        JsonNode body = objectMapper.readTree(response);
        assertThat(body.get("savedAt").asText()).isNotBlank();
    }

    private UUID createProject() throws Exception {
        String response = mockMvc.perform(post("/api/v1/projects").header(USER_ID_HEADER, sellerId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("projectId").asText());
    }

    private void createReward(UUID projectId) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/rewards", projectId)
                        .header(USER_ID_HEADER, sellerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "가습기 기본형", "price": 39000, "isUnlimited": false}
                                """))
                .andExpect(status().isCreated());
    }

    private UUID createPendingReviewProject() throws Exception {
        UUID projectId = createProject();
        mockMvc.perform(patch("/api/v1/projects/{projectId}/basic-info", projectId)
                        .header(USER_ID_HEADER, sellerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessType": "GENERAL",
                                  "categoryMajor": "홈·리빙",
                                  "categoryMinor": "인테리어",
                                  "goalAmount": 5000000,
                                  "privacyAgreed": true
                                }
                                """))
                .andExpect(status().isOk());
        createReward(projectId);
        mockMvc.perform(patch("/api/v1/projects/{projectId}/detail", projectId)
                        .header(USER_ID_HEADER, sellerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "무선 미니 가습기",
                                  "thumbnailImageUrl": "https://cdn.example.com/p/1.jpg",
                                  "introContent": {"text": "소개 본문"},
                                  "isDraft": false
                                }
                                """))
                .andExpect(status().isOk());
        return projectId;
    }

    private UUID createOngoingProject() throws Exception {
        UUID projectId = createPendingReviewProject();
        mockMvc.perform(patch("/api/v1/projects/{projectId}/review", projectId)
                        .header(USER_ID_HEADER, adminId)
                        .header(USER_ROLE_HEADER, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "fundingStartAt": "2026-10-01T00:00:00Z",
                                  "fundingDeadline": "2026-11-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isOk());
        return projectId;
    }

    private String readStatus(UUID projectId) throws Exception {
        String response = mockMvc.perform(get("/api/v1/projects/{projectId}", projectId)
                        .header(USER_ID_HEADER, sellerId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("status").asText();
    }
}
