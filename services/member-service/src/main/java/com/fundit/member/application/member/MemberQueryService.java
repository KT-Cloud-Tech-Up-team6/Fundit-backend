package com.fundit.member.application.member;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.member.infrastructure.persistence.member.MemberJpaEntity;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberQueryService {

    static final int MAX_NICKNAME_LOOKUP = 100;

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

    /**
     * 다른 서비스가 판매자명을 표시하려고 부르는 일괄 조회(내부 전용). 목록 카드 한 페이지분을 한 번에
     * 받으려는 용도라 건수에 상한을 둔다 — 없으면 id 수천 개로 한 번에 긁어갈 수 있다.
     *
     * <p>없는 id·탈퇴 회원은 결과에서 빠진다(에러가 아니다). 닉네임만 내보낸다 — 이름·전화번호는
     * 암호화해 보관하는 개인정보라 이 경로로 나가면 안 된다.
     */
    @Transactional(readOnly = true)
    public List<MemberNickname> findNicknames(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_NICKNAME_LOOKUP) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "한 번에 조회할 수 있는 회원은 %d명까지입니다.".formatted(MAX_NICKNAME_LOOKUP));
        }
        return memberJpaRepository.findAllByIdInAndDeletedAtIsNull(new HashSet<>(ids)).stream()
                .map(m -> new MemberNickname(m.getId(), m.getNickname()))
                .toList();
    }

    public record MemberProfile(UUID memberId, String name, String nickname, String phoneNumber) {
    }

    public record MemberNickname(UUID memberId, String nickname) {
    }
}
