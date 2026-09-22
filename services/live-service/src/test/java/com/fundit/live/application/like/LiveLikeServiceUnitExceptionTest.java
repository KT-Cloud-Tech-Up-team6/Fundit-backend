package com.fundit.live.application.like;

import com.fundit.common.error.BusinessException;
import com.fundit.live.infrastructure.persistence.like.LiveLikeJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveLikeServiceUnitExceptionTest {

    @Mock private LiveLikeJpaRepository likeRepository;
    @Mock private LiveSessionJpaRepository sessionRepository;

    @InjectMocks private LiveLikeService liveLikeService;

    private final UUID memberId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    @Test
    void 설정_중인_방송에는_좋아요가_적립되지_않는다() {
        // given — DRAFT는 쿼리에서 걸러져 빈 결과로 온다(존재 자체를 숨긴다, S10)
        given(sessionRepository.findPublicByPublicId(liveId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> liveLikeService.like(memberId, liveId))
                .isInstanceOf(BusinessException.class);
        verify(sessionRepository, never()).addLikeCount(anyLong(), anyInt());
    }
}
