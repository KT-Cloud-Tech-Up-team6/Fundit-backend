package com.fundit.auth.application.signup;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** member-service 동기 호출 포트. 구현체는 infrastructure/member에 있다. */
public interface MemberServiceClient {

    MemberProfile createProfile(CreateMemberProfileCommand command);

    record CreateMemberProfileCommand(
            UUID accountId,
            String email,
            String name,
            String phoneNumber,
            List<String> agreedTerms,
            Map<String, Object> address
    ) {
    }

    record MemberProfile(UUID memberId) {
    }
}
