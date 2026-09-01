package com.fundit.project.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * 요청을 보낸 사용자를 알려주는 아웃바운드 포트.
 * <p>
 * 인증 방식(게이트웨이가 JWT를 검증해 헤더로 신원을 넘길지, 각 서비스가 직접 검증할지)이
 * 아직 확정되지 않아 구현을 이 포트 뒤에 격리해 둔다. 확정되면 어댑터만 교체하면 된다
 * (services/project-service/CLAUDE.md "확인 상태" 참고).
 * <p>
 * 여기서 돌려주는 id가 auth-service의 accountId인지 member-service의 memberId인지도
 * 함께 확정되어야 한다 — projects.seller_id는 member 기준으로 정의되어 있는데
 * auth-service가 발급하는 토큰에는 accountId가 실린다.
 */
public interface CurrentUserProvider {

    enum Role {
        MEMBER,
        ADMIN
    }

    record CurrentUser(UUID id, Role role) {

        public boolean isAdmin() {
            return role == Role.ADMIN;
        }
    }

    /** 비로그인 허용 엔드포인트에서 사용한다. */
    Optional<CurrentUser> find();

    /** 로그인이 필수인 엔드포인트에서 사용한다. 없으면 401. */
    CurrentUser require();
}
