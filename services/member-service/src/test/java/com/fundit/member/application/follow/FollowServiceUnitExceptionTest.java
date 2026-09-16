package com.fundit.member.application.follow;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.member.infrastructure.persistence.follow.FollowJpaRepository;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 정상 흐름은 {@link FollowServiceUnitTest} 참고. */
@ExtendWith(MockitoExtension.class)
class FollowServiceUnitExceptionTest {

    @Mock
    private FollowJpaRepository followJpaRepository;
    @Mock
    private MemberJpaRepository memberJpaRepository;

    @InjectMocks
    private FollowService followService;

    @Test
    void 자기_자신을_팔로우하면_INVALID_INPUT() {
        // given
        UUID memberId = UUID.randomUUID();

        // when & then
        assertThatThrownBy(() -> followService.follow(memberId, memberId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
        verify(followJpaRepository, never()).insertIgnoringConflict(memberId, memberId);
    }

    @Test
    void 존재하지_않는_회원을_팔로우하면_NOT_FOUND() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        when(memberJpaRepository.findByIdAndDeletedAtIsNull(sellerId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> followService.follow(memberId, sellerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND);
        verify(followJpaRepository, never()).insertIgnoringConflict(memberId, sellerId);
    }
}
