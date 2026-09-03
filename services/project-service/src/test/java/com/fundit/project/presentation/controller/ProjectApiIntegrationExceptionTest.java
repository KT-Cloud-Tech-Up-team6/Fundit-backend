package com.fundit.project.presentation.controller;

import tools.jackson.databind.ObjectMapper;
import com.fundit.project.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ErrorCode가 실제 HTTP 상태 코드로 나가는지 확인한다. 도메인 규칙 자체는 단위 예외 테스트에서
 * 이미 검증했으므로, 여기서는 예외 핸들러를 거친 뒤의 응답 계약만 본다.
 */
@AutoConfigureMockMvc
@DisplayName("프로젝트 API 예외 통합")
@Sql("/sql/insert-categories.sql")
class ProjectApiIntegrationExceptionTest extends IntegrationTestSupport {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private static final String BASIC_INFO_BODY = """
            {
              "businessType": "GENERAL",
              "categoryMajor": "홈·리빙",
              "categoryMinor": "인테리어",
              "goalAmount": 5000000,
              "privacyAgreed": true
            }
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    private UUID sellerId;
    private UUID otherMemberId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        sellerId = UUID.randomUUID();
        otherMemberId = UUID.randomUUID();
        adminId = UUID.randomUUID();
    }

    @Nested
    class 인증과_권한 {

        @Test
        void 로그인하지_않고_개설하면_401이다() throws Exception {
            // given & when & then
            mockMvc.perform(post("/api/v1/projects"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void 식별자_형식이_아닌_헤더로_요청하면_401이다() throws Exception {
            // given & when & then
            mockMvc.perform(post("/api/v1/projects").header(USER_ID_HEADER, "not-a-uuid"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void 남의_프로젝트를_수정하려_하면_403이다() throws Exception {
            // given
            UUID projectId = createProject();

            // when & then
            mockMvc.perform(patch("/api/v1/projects/{projectId}/basic-info", projectId)
                            .header(USER_ID_HEADER, otherMemberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BASIC_INFO_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 일반_회원이_검수를_처리하려_하면_403이다() throws Exception {
            // given
            UUID projectId = createPendingReviewProject();

            // when & then
            mockMvc.perform(patch("/api/v1/projects/{projectId}/review", projectId)
                            .header(USER_ID_HEADER, otherMemberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"decision": "REJECT", "rejectReason": "사유"}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class 존재하지_않는_자원 {

        @Test
        void 없는_프로젝트를_조회하면_404다() throws Exception {
            // given & when & then
            mockMvc.perform(get("/api/v1/projects/{projectId}", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void 남의_작성중_프로젝트는_403이_아니라_404로_감춘다() throws Exception {
            // given
            UUID projectId = createProject();

            // when & then
            mockMvc.perform(get("/api/v1/projects/{projectId}", projectId)
                            .header(USER_ID_HEADER, otherMemberId))
                    .andExpect(status().isNotFound());
        }

        @Test
        void 삭제한_프로젝트는_404다() throws Exception {
            // given
            UUID projectId = createProject();
            mockMvc.perform(delete("/api/v1/projects/{projectId}", projectId).header(USER_ID_HEADER, sellerId))
                    .andExpect(status().isOk());

            // when & then
            mockMvc.perform(get("/api/v1/projects/{projectId}", projectId).header(USER_ID_HEADER, sellerId))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class 입력값_검증 {

        @Test
        void 필수_필드가_빠지면_400이다() throws Exception {
            // given
            UUID projectId = createProject();

            // when & then
            mockMvc.perform(patch("/api/v1/projects/{projectId}/basic-info", projectId)
                            .header(USER_ID_HEADER, sellerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"businessType": "GENERAL"}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 마스터에_없는_카테고리면_400이다() throws Exception {
            // given
            UUID projectId = createProject();

            // when & then
            mockMvc.perform(patch("/api/v1/projects/{projectId}/basic-info", projectId)
                            .header(USER_ID_HEADER, sellerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "businessType": "GENERAL",
                                      "categoryMajor": "없는분류",
                                      "categoryMinor": "없는상세",
                                      "goalAmount": 5000000,
                                      "privacyAgreed": true
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 검수_요청에_필수값이_빠지면_누락_항목이_응답에_담긴다() throws Exception {
            // given
            UUID projectId = createProject();

            // when & then — 무엇을 더 채워야 하는지 알려주지 않으면 판매자가 헤맨다
            mockMvc.perform(patch("/api/v1/projects/{projectId}/detail", projectId)
                            .header(USER_ID_HEADER, sellerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isDraft": false}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("rewards")));
        }
    }

    @Nested
    class 상태로_인한_거부 {

        @Test
        void 검수_중_임시저장은_423이다() throws Exception {
            // given
            UUID projectId = createPendingReviewProject();

            // when & then
            mockMvc.perform(patch("/api/v1/projects/{projectId}/detail", projectId)
                            .header(USER_ID_HEADER, sellerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "바꾼 제목", "isDraft": true}
                                    """))
                    .andExpect(status().isLocked());
        }

        @Test
        void 검수_중_재요청은_423이_아니라_409다() throws Exception {
            // given
            UUID projectId = createPendingReviewProject();

            // when & then — 같은 엔드포인트지만 "잠김"과 "중복 요청"은 구분해서 응답한다
            mockMvc.perform(patch("/api/v1/projects/{projectId}/detail", projectId)
                            .header(USER_ID_HEADER, sellerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isDraft": false}
                                    """))
                    .andExpect(status().isConflict());
        }

        @Test
        void 검수_중_리워드_등록은_423이다() throws Exception {
            // given
            UUID projectId = createPendingReviewProject();

            // when & then
            mockMvc.perform(post("/api/v1/projects/{projectId}/rewards", projectId)
                            .header(USER_ID_HEADER, sellerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "추가 리워드", "price": 1000, "isUnlimited": false}
                                    """))
                    .andExpect(status().isLocked());
        }

        @Test
        void 검수_중_삭제는_422다() throws Exception {
            // given
            UUID projectId = createPendingReviewProject();

            // when & then
            mockMvc.perform(delete("/api/v1/projects/{projectId}", projectId).header(USER_ID_HEADER, sellerId))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void 검수_대기가_아닌_프로젝트를_승인하려_하면_409다() throws Exception {
            // given
            UUID projectId = createProject();

            // when & then
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
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class 리워드_옵션 {

        @Test
        void sku가_중복이면_409다() throws Exception {
            // given
            UUID projectId = createProject();
            Long rewardId = createReward(projectId);
            String sku = "SKU-DUP-001";
            createOption(projectId, rewardId, "화이트", sku);

            // when & then
            mockMvc.perform(post("/api/v1/projects/{projectId}/rewards/{rewardId}/options", projectId, rewardId)
                            .header(USER_ID_HEADER, sellerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"optionName": "블랙", "sku": "%s", "initialStock": 5}
                                    """.formatted(sku)))
                    .andExpect(status().isConflict());
        }

        @Test
        void 초기_재고가_음수면_400이다() throws Exception {
            // given
            UUID projectId = createProject();
            Long rewardId = createReward(projectId);

            // when & then
            mockMvc.perform(post("/api/v1/projects/{projectId}/rewards/{rewardId}/options", projectId, rewardId)
                            .header(USER_ID_HEADER, sellerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"optionName": "화이트", "sku": "SKU-NEG-001", "initialStock": -1}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 다른_프로젝트의_리워드를_지우려_하면_404다() throws Exception {
            // given
            UUID projectId = createProject();
            UUID otherProjectId = createProject();
            Long rewardId = createReward(otherProjectId);

            // when & then
            mockMvc.perform(delete("/api/v1/projects/{projectId}/rewards/{rewardId}", projectId, rewardId)
                            .header(USER_ID_HEADER, sellerId))
                    .andExpect(status().isNotFound());
        }
    }

    private UUID createProject() throws Exception {
        String response = mockMvc.perform(post("/api/v1/projects").header(USER_ID_HEADER, sellerId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("projectId").asText());
    }

    private Long createReward(UUID projectId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/projects/{projectId}/rewards", projectId)
                        .header(USER_ID_HEADER, sellerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "가습기 기본형", "price": 39000, "isUnlimited": false}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("rewardId").asLong();
    }

    private void createOption(UUID projectId, Long rewardId, String optionName, String sku) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/rewards/{rewardId}/options", projectId, rewardId)
                        .header(USER_ID_HEADER, sellerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"optionName": "%s", "sku": "%s", "initialStock": 10}
                                """.formatted(optionName, sku)))
                .andExpect(status().isCreated());
    }

    private UUID createPendingReviewProject() throws Exception {
        UUID projectId = createProject();
        mockMvc.perform(patch("/api/v1/projects/{projectId}/basic-info", projectId)
                        .header(USER_ID_HEADER, sellerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BASIC_INFO_BODY))
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
}
