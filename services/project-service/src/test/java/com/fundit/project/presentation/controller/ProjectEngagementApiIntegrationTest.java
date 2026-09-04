package com.fundit.project.presentation.controller;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공개된 프로젝트에 달리는 부가 기능(새소식·커뮤니티·후기·팔로우·펀딩현황) API.
 * 프로젝트 개설 흐름은 라이프사이클 테스트에서 이미 다루므로, 여기서는 진행중 프로젝트를
 * 직접 만들어 두고 각 엔드포인트의 응답 계약만 확인한다.
 */
@AutoConfigureMockMvc
@DisplayName("프로젝트 부가기능 API 통합")
@Sql("/sql/insert-categories.sql")
class ProjectEngagementApiIntegrationTest extends IntegrationTestSupport {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProjectRepository projectRepository;

    private UUID projectId;
    private UUID sellerId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        sellerId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        Project project = projectRepository.save(Project.createDraft(sellerId));
        projectId = project.getPublicId();
        // 부가 기능은 공개된 프로젝트를 전제로 하므로 상태만 진행중으로 맞춰 둔다
        jdbcTemplate.update("UPDATE projects SET status = 'ONGOING' WHERE id = ?", project.getId());
    }

    @Nested
    class 새소식 {

        @Test
        void 판매자가_등록하면_목록에_나타난다() throws Exception {
            // given
            createNotice("제작과정", "1차 생산 완료");

            // when & then
            mockMvc.perform(get("/api/v1/projects/{projectId}/notices", projectId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].noticeType").value("제작과정"))
                    .andExpect(jsonPath("$.content[0].commentCount").value(0));
        }

        @Test
        void 유형으로_걸러_조회할_수_있다() throws Exception {
            // given
            createNotice("제작과정", "1차 생산 완료");
            createNotice("이벤트", "얼리버드 마감 임박");

            // when & then
            mockMvc.perform(get("/api/v1/projects/{projectId}/notices", projectId).param("noticeType", "이벤트"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("얼리버드 마감 임박"));
        }

        @Test
        void 인기순은_댓글이_많은_새소식을_앞에_둔다() throws Exception {
            // given
            createNotice("제작과정", "댓글 없는 새소식");
            Long popular = createNotice("이벤트", "댓글 달린 새소식");
            addComment(popular, "기대돼요");

            // when & then
            mockMvc.perform(get("/api/v1/projects/{projectId}/notices", projectId).param("sort", "POPULAR"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].title").value("댓글 달린 새소식"));
        }

        @Test
        void 댓글을_달면_목록에_나타난다() throws Exception {
            // given
            Long noticeId = createNotice("제작과정", "1차 생산 완료");
            addComment(noticeId, "기대돼요");

            // when & then
            mockMvc.perform(get("/api/v1/projects/{projectId}/notices/{noticeId}/comments", projectId, noticeId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].content").value("기대돼요"));
        }

        @Test
        void 지원하지_않는_유형이면_400이다() throws Exception {
            // given & when & then
            mockMvc.perform(post("/api/v1/projects/{projectId}/notices", projectId)
                            .header(USER_ID_HEADER, sellerId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"noticeType": "없는유형", "title": "제목", "content": "본문"}
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class 커뮤니티 {

        @Test
        void 질문을_올리면_미답변으로_조회된다() throws Exception {
            // given
            createQuestion("배송은 언제 되나요?");

            // when & then
            mockMvc.perform(get("/api/v1/projects/{projectId}/community", projectId).param("answered", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].content").value("배송은 언제 되나요?"));
        }

        @Test
        void 판매자가_답변하면_답변완료로_넘어간다() throws Exception {
            // given
            Long postId = createQuestion("배송은 언제 되나요?");
            answerQuestion(postId, "다음 주에 발송됩니다");

            // when & then
            mockMvc.perform(get("/api/v1/projects/{projectId}/community", projectId).param("answered", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].answer.content").value("다음 주에 발송됩니다"));
        }

        @Test
        void 다시_답변하면_기존_답변이_교체된다() throws Exception {
            // given
            Long postId = createQuestion("배송은 언제 되나요?");
            answerQuestion(postId, "다음 주에 발송됩니다");

            // when
            answerQuestion(postId, "이번 주로 앞당겨졌습니다");

            // then
            mockMvc.perform(get("/api/v1/projects/{projectId}/community", projectId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].answer.content").value("이번 주로 앞당겨졌습니다"));
        }

        @Test
        void 판매자가_아니면_답변할_수_없다() throws Exception {
            // given
            Long postId = createQuestion("배송은 언제 되나요?");

            // when & then
            mockMvc.perform(post("/api/v1/projects/{projectId}/community/{postId}/answers", projectId, postId)
                            .header(USER_ID_HEADER, memberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"content": "제가 대신 답할게요"}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class 서포터_후기 {

        @Test
        void 후기가_없으면_빈_목록이다() throws Exception {
            // given & when & then
            mockMvc.perform(get("/api/v1/projects/{projectId}/reviews", projectId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
        }

        @Test
        void 자격을_확인할_수_없으면_403이다() throws Exception {
            // given & when & then — 스텁은 소유/배송을 둘 다 false로 두므로 소유권 검사에서 403이 난다
            mockMvc.perform(post("/api/v1/projects/{projectId}/reviews", projectId)
                            .header(USER_ID_HEADER, memberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"fundingId": 7001, "content": "잘 받았습니다"}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class 팔로우와_오픈알림 {

        @Test
        void 팔로우는_두_번_눌러도_성공한다() throws Exception {
            // given & when & then
            mockMvc.perform(post("/api/v1/projects/{projectId}/follow", projectId).header(USER_ID_HEADER, memberId))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/projects/{projectId}/follow", projectId).header(USER_ID_HEADER, memberId))
                    .andExpect(status().isOk());
        }

        @Test
        void 팔로우하지_않은_상태에서_해제해도_성공한다() throws Exception {
            // given & when & then
            mockMvc.perform(delete("/api/v1/projects/{projectId}/follow", projectId).header(USER_ID_HEADER, memberId))
                    .andExpect(status().isOk());
        }

        @Test
        void 오픈알림_신청과_취소가_모두_통과한다() throws Exception {
            // given & when & then
            mockMvc.perform(post("/api/v1/projects/{projectId}/notify", projectId).header(USER_ID_HEADER, memberId))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/api/v1/projects/{projectId}/notify", projectId).header(USER_ID_HEADER, memberId))
                    .andExpect(status().isOk());
        }

        @Test
        void 로그인하지_않으면_팔로우할_수_없다() throws Exception {
            // given & when & then
            mockMvc.perform(post("/api/v1/projects/{projectId}/follow", projectId))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class 리워드_조회 {

        @Test
        void 옵션과_재고가_함께_내려온다() throws Exception {
            // given
            Long rewardId = createReward();
            createOption(rewardId);

            // when & then
            mockMvc.perform(get("/api/v1/projects/{projectId}/rewards", projectId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rewards.length()").value(1))
                    .andExpect(jsonPath("$.rewards[0].options.length()").value(1))
                    .andExpect(jsonPath("$.rewards[0].options[0].optionName").value("화이트"));
        }

        @Test
        void 고시_정보는_리워드마다_따로_내려온다() throws Exception {
            // given
            createReward();

            // when & then
            mockMvc.perform(get("/api/v1/projects/{projectId}/reward-info", projectId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rewards.length()").value(1));
        }
    }

    @Nested
    class 펀딩_현황 {

        @Test
        void 판매자는_집계값을_볼_수_있다() throws Exception {
            // given & when & then
            mockMvc.perform(get("/api/v1/projects/{projectId}/funding-summary", projectId)
                            .header(USER_ID_HEADER, sellerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentAmount").value(0))
                    .andExpect(jsonPath("$.openNotifyCount").value(0));
        }

        @Test
        void 오픈알림_신청_수가_반영된다() throws Exception {
            // given
            mockMvc.perform(post("/api/v1/projects/{projectId}/notify", projectId).header(USER_ID_HEADER, memberId))
                    .andExpect(status().isOk());

            // when & then
            mockMvc.perform(get("/api/v1/projects/{projectId}/funding-summary", projectId)
                            .header(USER_ID_HEADER, sellerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.openNotifyCount").value(1));
        }

        @Test
        void 판매자가_아니면_볼_수_없다() throws Exception {
            // given & when & then
            mockMvc.perform(get("/api/v1/projects/{projectId}/funding-summary", projectId)
                            .header(USER_ID_HEADER, memberId))
                    .andExpect(status().isForbidden());
        }
    }

    private Long createNotice(String noticeType, String title) throws Exception {
        String response = mockMvc.perform(post("/api/v1/projects/{projectId}/notices", projectId)
                        .header(USER_ID_HEADER, sellerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"noticeType": "%s", "title": "%s", "content": "본문"}
                                """.formatted(noticeType, title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("noticeId").asLong();
    }

    private void addComment(Long noticeId, String content) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/notices/{noticeId}/comments", projectId, noticeId)
                        .header(USER_ID_HEADER, memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "%s"}
                                """.formatted(content)))
                .andExpect(status().isCreated());
    }

    private Long createQuestion(String content) throws Exception {
        String response = mockMvc.perform(post("/api/v1/projects/{projectId}/community/questions", projectId)
                        .header(USER_ID_HEADER, memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"postType": "QUESTION", "content": "%s"}
                                """.formatted(content)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("postId").asLong();
    }

    private void answerQuestion(Long postId, String content) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/community/{postId}/answers", projectId, postId)
                        .header(USER_ID_HEADER, sellerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "%s"}
                                """.formatted(content)))
                .andExpect(status().isOk());
    }

    private Long createReward() throws Exception {
        String response = mockMvc.perform(post("/api/v1/projects/{projectId}/rewards", projectId)
                        .header(USER_ID_HEADER, sellerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "가습기 기본형", "price": 39000, "isUnlimited": false,
                                 "categoryType": "ELECTRONICS", "disclosure": {"모델명": "H-100"}}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("rewardId").asLong();
    }

    private void createOption(Long rewardId) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/rewards/{rewardId}/options", projectId, rewardId)
                        .header(USER_ID_HEADER, sellerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"optionName": "화이트", "sku": "SKU-ENG-001", "initialStock": 10}
                                """))
                .andExpect(status().isCreated());
    }
}
