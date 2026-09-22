package com.fundit.live.application.like;

import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.like.LiveLikeId;
import com.fundit.live.infrastructure.persistence.like.LiveLikeJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveLikeServiceUnitTest {

    @Mock private LiveLikeJpaRepository likeRepository;
    @Mock private LiveSessionJpaRepository sessionRepository;

    @InjectMocks private LiveLikeService liveLikeService;

    private final UUID memberId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    private void givenSession(int likeCount) {
        given(sessionRepository.findPublicByPublicId(liveId)).willReturn(Optional.of(
                LiveSessionJpaEntity.builder().id(1L).publicId(liveId).channelId(1L)
                        .projectId(UUID.randomUUID()).status(LiveStatus.LIVE).likeCount(likeCount).build()));
    }

    @Test
    void 처음_누르면_카운트가_올라간다() {
        // given
        givenSession(0);
        given(likeRepository.insertIgnoringConflict(1L, memberId)).willReturn(1);

        // when
        int likeCount = liveLikeService.like(memberId, liveId);

        // then
        assertThat(likeCount).isEqualTo(1);
        verify(sessionRepository).addLikeCount(1L, 1);
    }

    @Test
    void 두_번_눌러도_카운트는_한_번만_오른다() {
        // given — 네트워크 재시도로 같은 요청이 두 번 와도 결과가 같아야 한다
        givenSession(1);
        given(likeRepository.insertIgnoringConflict(1L, memberId)).willReturn(0);

        // when
        int likeCount = liveLikeService.like(memberId, liveId);

        // then
        assertThat(likeCount).isEqualTo(1);
        verify(sessionRepository, never()).addLikeCount(anyLong(), anyInt());
    }

    @Test
    void 취소하면_카운트가_내려간다() {
        // given
        givenSession(1);
        given(likeRepository.deleteByIds(1L, memberId)).willReturn(1);

        // when
        int likeCount = liveLikeService.unlike(memberId, liveId);

        // then
        assertThat(likeCount).isZero();
        verify(sessionRepository).addLikeCount(1L, -1);
    }

    @Test
    void 누른_적_없는_취소는_카운트를_건드리지_않는다() {
        // given — 취소도 idempotent다
        givenSession(0);
        given(likeRepository.deleteByIds(1L, memberId)).willReturn(0);

        // when
        int likeCount = liveLikeService.unlike(memberId, liveId);

        // then
        assertThat(likeCount).isZero();
        verify(sessionRepository, never()).addLikeCount(anyLong(), anyInt());
    }

    @Test
    void 눌렀으면_liked는_true다() {
        // given
        givenSession(1);
        given(likeRepository.existsById(new LiveLikeId(1L, memberId))).willReturn(true);

        // when & then
        assertThat(liveLikeService.isLiked(memberId, liveId)).isTrue();
    }

    @Test
    void 누른_적_없으면_liked는_false다() {
        // given
        givenSession(0);
        given(likeRepository.existsById(new LiveLikeId(1L, memberId))).willReturn(false);

        // when & then
        assertThat(liveLikeService.isLiked(memberId, liveId)).isFalse();
    }
}
