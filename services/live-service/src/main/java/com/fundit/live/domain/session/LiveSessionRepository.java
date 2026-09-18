package com.fundit.live.domain.session;

import java.util.Optional;
import java.util.UUID;

/**
 * 복잡 애그리거트의 아웃바운드 포트(persistence-convention.md 1번).
 * 구현체는 infrastructure에 둔다.
 *
 * <p>조회 메서드가 전부 sellerId를 함께 받는 이유: 소유권 검증이 이 서비스 인가의 전부인데
 * "조회 후 검사"로 두면 어느 호출 하나에서 검사를 빠뜨려도 컴파일이 통과한다.
 * 쿼리에 묶어두면 빠뜨릴 수 없다(S4).
 */
public interface LiveSessionRepository {

    LiveSession save(LiveSession session);

    Optional<LiveSession> findOwned(UUID publicId, UUID sellerId);
}
