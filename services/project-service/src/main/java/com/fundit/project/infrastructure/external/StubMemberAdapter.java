package com.fundit.project.infrastructure.external;

import com.fundit.project.application.port.MemberPort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * member-service 미연동 상태의 임시 어댑터. 닉네임을 못 가져오면 응답에서 null로 노출된다.
 */
@Component
public class StubMemberAdapter implements MemberPort {

    @Override
    public Map<UUID, String> findNicknames(Collection<UUID> memberIds) {
        return Map.of();
    }

    @Override
    public long countWishes(Long projectId) {
        return 0L;
    }
}
