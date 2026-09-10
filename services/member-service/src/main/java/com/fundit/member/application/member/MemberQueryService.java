package com.fundit.member.application.member;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.member.infrastructure.persistence.member.MemberJpaEntity;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberQueryService {

    private final MemberJpaRepository memberJpaRepository;

    @Transactional(readOnly = true)
    public MemberProfile getMe(UUID accountId) {
        MemberJpaEntity member = memberJpaRepository.findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return new MemberProfile(member.getId(), member.getName(), member.getNickname(), member.getPhoneNumber());
    }

    /**
     * 본인인증으로 확인된 휴대폰번호가 이 계정 주인의 것인지 확인한다(auth-service의 소셜 계정 연동용).
     *
     * <p>정규화 없이 그대로 비교하는 이유: 여기 저장된 번호는 회원가입 때 auth-service가 넘긴
     * {@code verifiedIdentity.phoneNumber()}, 즉 본인인증 제공자가 준 값이다. 연동 시점에 비교하는
     * 값도 같은 제공자에서 나오므로 표기가 갈릴 여지가 없다. 사용자가 타이핑한 값이 섞이기 시작하면
     * 그때 정규화를 넣는다.
     *
     * <p>계정이 없으면 예외가 아니라 {@code false}다 — 호출자에게 "그 계정이 존재하지 않는다"와
     * "번호가 다르다"를 구분해 알려줄 이유가 없다.
     */
    @Transactional(readOnly = true)
    public boolean phoneMatches(UUID accountId, String phoneNumber) {
        return memberJpaRepository.findByIdAndDeletedAtIsNull(accountId)
                .map(member -> member.getPhoneNumber().equals(phoneNumber))
                .orElse(false);
    }

    public record MemberProfile(UUID memberId, String name, String nickname, String phoneNumber) {
    }
}
