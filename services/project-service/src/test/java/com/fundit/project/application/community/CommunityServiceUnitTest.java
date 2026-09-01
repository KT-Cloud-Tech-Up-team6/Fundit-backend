package com.fundit.project.application.community;

import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.application.port.MemberPort;
import com.fundit.project.application.port.NotificationPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.domain.community.PostType;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.infrastructure.persistence.community.CommunityAnswerJpaEntity;
import com.fundit.project.infrastructure.persistence.community.CommunityAnswerJpaRepository;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaEntity;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CommunityService")
@ExtendWith(MockitoExtension.class)
class CommunityServiceUnitTest {

    private static final Long POST_ID = 401L;

    @Mock
    private CommunityPostJpaRepository postJpaRepository;
    @Mock
    private CommunityAnswerJpaRepository answerJpaRepository;
    @Mock
    private ProjectAccessGuard accessGuard;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private MemberPort memberPort;
    @Mock
    private NotificationPort notificationPort;

    @InjectMocks
    private CommunityService communityService;

    private static CommunityPostJpaEntity post(Long id) {
        return CommunityPostJpaEntity.builder()
                .id(id)
                .projectId(ProjectFixture.PROJECT_ID)
                .memberId(ProjectFixture.OTHER_MEMBER_ID)
                .postType(PostType.QUESTION)
                .content("배송은 언제 되나요?")
                .build();
    }

    private void givenPublicProject() {
        when(currentUserProvider.find()).thenReturn(Optional.empty());
        when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, null)).thenReturn(ProjectFixture.ongoing());
    }

    private CurrentUser givenSellerOwnsProject() {
        CurrentUser seller = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
        when(currentUserProvider.require()).thenReturn(seller);
        when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.ongoing());
        return seller;
    }

    @Nested
    class 목록_조회 {

        @Test
        void 답변이_함께_묶여_나온다() {
            // given
            givenPublicProject();
            when(postJpaRepository.findByProjectIdOrderByCreatedAtDesc(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(post(POST_ID)));
            when(answerJpaRepository.findByPostIdIn(List.of(POST_ID))).thenReturn(List.of(
                    CommunityAnswerJpaEntity.builder().postId(POST_ID).content("다음 주 발송입니다").build()));
            when(memberPort.findNicknames(List.of(ProjectFixture.OTHER_MEMBER_ID)))
                    .thenReturn(Map.of(ProjectFixture.OTHER_MEMBER_ID, "궁금이"));

            // when
            var posts = communityService.list(ProjectFixture.PUBLIC_ID, null, null);

            // then
            assertThat(posts).hasSize(1);
            assertThat(posts.getFirst().answer().getContent()).isEqualTo("다음 주 발송입니다");
            assertThat(posts.getFirst().nickname()).isEqualTo("궁금이");
        }

        @Test
        void 미답변만_필터링할_수_있다() {
            // given
            givenPublicProject();
            when(postJpaRepository.findByProjectIdOrderByCreatedAtDesc(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(post(401L), post(402L)));
            when(answerJpaRepository.findByPostIdIn(List.of(401L, 402L))).thenReturn(List.of(
                    CommunityAnswerJpaEntity.builder().postId(401L).content("답변").build()));
            when(memberPort.findNicknames(any())).thenReturn(Map.of());

            // when
            var posts = communityService.list(ProjectFixture.PUBLIC_ID, null, false);

            // then
            assertThat(posts).hasSize(1);
            assertThat(posts.getFirst().post().getId()).isEqualTo(402L);
        }

        @Test
        void 답변완료만_필터링할_수_있다() {
            // given
            givenPublicProject();
            when(postJpaRepository.findByProjectIdOrderByCreatedAtDesc(ProjectFixture.PROJECT_ID))
                    .thenReturn(List.of(post(401L), post(402L)));
            when(answerJpaRepository.findByPostIdIn(List.of(401L, 402L))).thenReturn(List.of(
                    CommunityAnswerJpaEntity.builder().postId(401L).content("답변").build()));
            when(memberPort.findNicknames(any())).thenReturn(Map.of());

            // when
            var posts = communityService.list(ProjectFixture.PUBLIC_ID, null, true);

            // then
            assertThat(posts).hasSize(1);
            assertThat(posts.getFirst().post().getId()).isEqualTo(401L);
        }

        @Test
        void 유형을_주면_해당_유형만_조회한다() {
            // given
            givenPublicProject();
            when(postJpaRepository.findByProjectIdAndPostTypeOrderByCreatedAtDesc(
                    ProjectFixture.PROJECT_ID, PostType.QUESTION)).thenReturn(List.of(post(POST_ID)));
            when(answerJpaRepository.findByPostIdIn(List.of(POST_ID))).thenReturn(List.of());
            when(memberPort.findNicknames(any())).thenReturn(Map.of());

            // when
            communityService.list(ProjectFixture.PUBLIC_ID, PostType.QUESTION, null);

            // then
            verify(postJpaRepository, never()).findByProjectIdOrderByCreatedAtDesc(ProjectFixture.PROJECT_ID);
        }
    }

    @Nested
    class 게시글_작성 {

        @Test
        void 로그인_회원이면_작성할_수_있다() {
            // given
            CurrentUser member = new CurrentUser(ProjectFixture.OTHER_MEMBER_ID, Role.MEMBER);
            when(currentUserProvider.require()).thenReturn(member);
            when(accessGuard.findVisible(ProjectFixture.PUBLIC_ID, ProjectFixture.OTHER_MEMBER_ID))
                    .thenReturn(ProjectFixture.ongoing());
            when(postJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            var saved = communityService.createPost(ProjectFixture.PUBLIC_ID, PostType.QUESTION, "질문 있어요");

            // then
            assertThat(saved.getMemberId()).isEqualTo(ProjectFixture.OTHER_MEMBER_ID);
            assertThat(saved.getPostType()).isEqualTo(PostType.QUESTION);
        }
    }

    @Nested
    class 답변 {

        @Test
        void 첫_답변이면_새로_만들어진다() {
            // given
            CurrentUser seller = givenSellerOwnsProject();
            when(postJpaRepository.findById(POST_ID)).thenReturn(Optional.of(post(POST_ID)));
            when(answerJpaRepository.findByPostId(POST_ID)).thenReturn(Optional.empty());
            when(answerJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            var saved = communityService.upsertAnswer(ProjectFixture.PUBLIC_ID, POST_ID, "다음 주 발송입니다");

            // then
            assertThat(saved.getPostId()).isEqualTo(POST_ID);
            assertThat(saved.getSellerId()).isEqualTo(seller.id());
            assertThat(saved.getContent()).isEqualTo("다음 주 발송입니다");
        }

        @Test
        void 이미_답변이_있으면_내용만_바뀐다() {
            // given
            givenSellerOwnsProject();
            CommunityAnswerJpaEntity existing = CommunityAnswerJpaEntity.builder()
                    .id(9L).postId(POST_ID).sellerId(ProjectFixture.SELLER_ID).content("이전 답변").build();
            when(postJpaRepository.findById(POST_ID)).thenReturn(Optional.of(post(POST_ID)));
            when(answerJpaRepository.findByPostId(POST_ID)).thenReturn(Optional.of(existing));
            when(answerJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            var saved = communityService.upsertAnswer(ProjectFixture.PUBLIC_ID, POST_ID, "수정된 답변");

            // then
            assertThat(saved.getId()).isEqualTo(9L);
            assertThat(saved.getContent()).isEqualTo("수정된 답변");
        }

        @Test
        void 질문자에게_알림이_발송된다() {
            // given
            givenSellerOwnsProject();
            when(postJpaRepository.findById(POST_ID)).thenReturn(Optional.of(post(POST_ID)));
            when(answerJpaRepository.findByPostId(POST_ID)).thenReturn(Optional.empty());
            when(answerJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            communityService.upsertAnswer(ProjectFixture.PUBLIC_ID, POST_ID, "답변");

            // then
            verify(notificationPort).notifyQuestionAnswered(POST_ID, ProjectFixture.OTHER_MEMBER_ID);
        }
    }
}
