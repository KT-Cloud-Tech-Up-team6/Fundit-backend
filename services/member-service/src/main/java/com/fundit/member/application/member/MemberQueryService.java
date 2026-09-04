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

    public record MemberProfile(UUID memberId, String name, String nickname, String phoneNumber) {
    }
}
