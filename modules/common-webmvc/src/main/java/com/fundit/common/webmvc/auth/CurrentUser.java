package com.fundit.common.webmvc.auth;

import java.util.List;
import java.util.UUID;

/**
 * 현재 요청을 보낸 사용자의 인증 정보. 게이트웨이가 JWT에서 꺼내 헤더로 넘겨준 값만 담는다.
 *
 * <p>email은 담지 않는다 — auth-service가 발급하는 JWT에 email 클레임이 없고,
 * member-service도 email을 저장하지 않는다(auth-service 소관). 필요해지면 토큰 발급부터
 * 같이 바꿔야 하므로 지금 자리만 잡아두지 않는다.
 *
 * @param id    계정 ID. auth-service {@code accounts.id} == member-service {@code members.id}
 * @param roles 권한 목록. 현재 auth-service는 단일 role만 발급하므로 원소가 하나다
 */
public record CurrentUser(UUID id, List<String> roles) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
