package com.fundit.member.application.follow;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.member.infrastructure.persistence.follow.FollowJpaRepository;
import com.fundit.member.infrastructure.persistence.follow.FollowView;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 메이커 팔로우(MEMBER-007). 팔로우·언팔로우 모두 idempotent하다.
 *
 * <p>대상이 "판매자"인지는 검증하지 않는다 — members에 판매자 구분 컬럼이 없다
 * (isSeller/isBuyer가 둘 다 항상 true). 존재하는 회원인지만 확인한다.
 */
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowJpaRepository followJpaRepository;
    private final MemberJpaRepository memberJpaRepository;

    @Transactional
    public void follow(UUID memberId, UUID sellerId) {
        if (memberId.equals(sellerId)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "자기 자신은 팔로우할 수 없습니다.");
        }
        requireExistingMember(sellerId);
        followJpaRepository.insertIgnoringConflict(memberId, sellerId);
    }

    /**
     * 언팔로우는 대상 존재 여부를 확인하지 않는다 — 없는 대상이면 지울 행이 없어 그대로 성공이고,
     * 확인해서 404를 주면 "이미 지워진 걸 지우는" 재시도가 실패로 바뀐다.
     */
    @Transactional
    public void unfollow(UUID memberId, UUID sellerId) {
        followJpaRepository.deleteByMemberIdAndSellerId(memberId, sellerId);
    }

    @Transactional(readOnly = true)
    public Page<FollowView> getFollows(UUID memberId, Pageable pageable) {
        return followJpaRepository.findViewsByMemberId(memberId, pageable);
    }

    private void requireExistingMember(UUID sellerId) {
        memberJpaRepository.findByIdAndDeletedAtIsNull(sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
