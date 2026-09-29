package com.fundit.project.infrastructure.sellerprofile;

import com.fundit.project.application.project.SellerProfileClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** member-service {@code GET /internal/v1/members/nicknames}로 판매자 표시명(닉네임)을 일괄 조회한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberServiceSellerProfileClient implements SellerProfileClient {

    /** member 쪽 한 번 조회 상한(MemberQueryService.MAX_NICKNAME_LOOKUP)과 맞춘다. 넘으면 나눠 부른다. */
    static final int CHUNK_SIZE = 100;

    private final RestClient memberServiceRestClient;

    @Override
    public Map<UUID, String> getDisplayNames(Collection<UUID> sellerIds) {
        List<UUID> ids = sellerIds.stream().filter(Objects::nonNull).distinct().toList();
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

    /** 실패는 빈 목록 — 판매자명 때문에 상세·목록·색인 이벤트가 실패하면 안 된다(인터페이스 주석). */
    private List<MemberNickname> fetch(List<UUID> ids) {
        try {
            MemberNickname[] body = memberServiceRestClient.get()
                    .uri(uri -> uri.path("/internal/v1/members/nicknames").queryParam("ids", ids.toArray()).build())
                    .retrieve()
                    .body(MemberNickname[].class);
            return body == null ? List.of() : Arrays.asList(body);
        } catch (RestClientException e) {
            log.warn("판매자 닉네임 조회 실패, 판매자명 없이 진행. ids={}", ids.size(), e);
            return List.of();
        }
    }

    private record MemberNickname(UUID memberId, String nickname) {
    }
}
