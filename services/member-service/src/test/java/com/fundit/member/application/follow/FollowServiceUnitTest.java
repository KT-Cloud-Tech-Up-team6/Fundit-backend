package com.fundit.member.application.follow;

import com.fundit.member.infrastructure.persistence.follow.FollowJpaRepository;
import com.fundit.member.infrastructure.persistence.follow.FollowView;
import com.fundit.member.infrastructure.persistence.member.MemberJpaEntity;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceUnitTest {

    @Mock
    private FollowJpaRepository followJpaRepository;
    @Mock
    private MemberJpaRepository memberJpaRepository;

    @InjectMocks
    private FollowService followService;

    @Test
    void 팔로우하면_중복을_무시하고_저장한다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        when(memberJpaRepository.findByIdAndDeletedAtIsNull(sellerId))
                .thenReturn(Optional.of(MemberJpaEntity.builder().id(sellerId).build()));

        // when
        followService.follow(memberId, sellerId);

        // then
        verify(followJpaRepository).insertIgnoringConflict(memberId, sellerId);
    }

    /** 이미 지워진 걸 지우는 재시도가 404로 바뀌면 안 되므로 대상 존재를 확인하지 않는다. */
    @Test
    void 언팔로우는_대상_존재를_확인하지_않는다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        // when
        followService.unfollow(memberId, sellerId);

        // then
        verify(followJpaRepository).deleteByMemberIdAndSellerId(memberId, sellerId);
    }

    @Test
    void 팔로우_목록을_조회한다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Page<FollowView> page = new PageImpl<>(List.of(
                new FollowView(sellerId, "홍길동", "길동", Instant.now())));
        when(followJpaRepository.findViewsByMemberId(memberId, PageRequest.of(0, 20))).thenReturn(page);

        // when
        Page<FollowView> result = followService.getFollows(memberId, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).sellerName()).isEqualTo("홍길동");
    }
}
