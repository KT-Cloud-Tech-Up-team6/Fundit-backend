package com.fundit.auth.application.signup;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** member-service 동기 호출 포트. 구현체는 infrastructure/member에 있다. */
public interface MemberServiceClient {

    MemberProfile createProfile(CreateMemberProfileCommand command);

    /**
     * 본인인증으로 확인된 휴대폰번호가 이 계정 주인의 것인지 묻는다(소셜 계정 연동의 본인 확인).
     * 번호로 계정을 찾는 게 아니라 이미 아는 계정에 대해 맞는지만 묻는다 — 반대 방향이면
     * 번호만 넣어보며 가입 여부를 캐낼 수 있다.
     */
    boolean phoneMatches(UUID accountId, String phoneNumber);

    /**
     * {@code nickname}은 없을 수 있다 — 사용자가 입력하지 않았거나 소셜 제공자가 주지 않은 경우.
     * <b>실명({@code name})으로 대신 채우지 않는다</b>: 닉네임은 타인에게 보이는 값이다(security.md S9).
     */
    record CreateMemberProfileCommand(
            UUID accountId,
            String email,
            String name,
            String nickname,
            String phoneNumber,
            List<String> agreedTerms,
            Map<String, Object> address
    ) {
    }

    record MemberProfile(UUID memberId) {
    }
}
