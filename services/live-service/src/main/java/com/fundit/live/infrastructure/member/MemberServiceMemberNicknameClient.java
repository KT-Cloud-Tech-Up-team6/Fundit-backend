package com.fundit.live.infrastructure.member;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.member.MemberNicknameClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** member-service {@code GET /internal/v1/members/nicknames}를 호출한다. */
@Component
@RequiredArgsConstructor
public class MemberServiceMemberNicknameClient implements MemberNicknameClient {

    /** member 쪽 한 번 조회 상한(MemberQueryService.MAX_NICKNAME_LOOKUP)과 맞춘다. 넘으면 나눠 부른다. */
    static final int CHUNK_SIZE = 100;

    private final RestClient memberServiceRestClient;

    @Override
    public Map<UUID, String> findNicknames(Collection<UUID> memberIds) {
        List<UUID> ids = memberIds.stream().distinct().toList();
        Map<UUID, String> result = new HashMap<>();
        for (int i = 0; i < ids.size(); i += CHUNK_SIZE) {
            List<UUID> chunk = ids.subList(i, Math.min(i + CHUNK_SIZE, ids.size()));
            for (MemberNickname m : fetch(chunk)) {
                // 응답을 그대로 믿지 않는다(S7) — 비어 있는 항목은 버린다.
                if (m != null && m.memberId() != null && m.nickname() != null) {
                    result.put(m.memberId(), m.nickname());
                }
            }
        }
        return result;
    }

    private List<MemberNickname> fetch(List<UUID> ids) {
        try {
            MemberNickname[] body = memberServiceRestClient.get()
                    .uri(uri -> uri.path("/internal/v1/members/nicknames").queryParam("ids", ids.toArray()).build())
                    .retrieve()
                    .body(MemberNickname[].class);
            return body == null ? List.of() : Arrays.asList(body);
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private record MemberNickname(UUID memberId, String nickname) {
    }
}
