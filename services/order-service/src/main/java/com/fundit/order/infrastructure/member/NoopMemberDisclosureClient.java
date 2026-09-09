package com.fundit.order.infrastructure.member;

import com.fundit.order.application.member.MemberDisclosureClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** {@link MemberDisclosureClient} 클래스 주석 참고 — member-service 연동 전 placeholder. */
@Component
public class NoopMemberDisclosureClient implements MemberDisclosureClient {

    @Override
    public boolean isPublicConsent(UUID memberId) {
        return false;
    }
}
