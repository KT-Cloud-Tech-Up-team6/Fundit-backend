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

    /**
     * 상태를 바꾸는 경로(시작·종료) 전용. 행을 잠가 동시 요청을 직렬화한다 —
     * 잠그지 않으면 둘 다 상태 검사를 통과해 외부 자원이 중복 생성된다.
     */
    Optional<LiveSession> findOwnedForUpdate(UUID publicId, UUID sellerId);

    /**
     * 소유권 없이 조회한다. <b>내부 전용 경로(AI 결과 수신)에서만 쓴다</b> —
     * 호출자가 사용자가 아니라 AI 서버라 대조할 sellerId가 없다.
     * 사용자 요청 경로에서 이걸 쓰면 인가가 사라진다.
     */
    Optional<LiveSession> findOwnedAny(UUID publicId);

    /**
     * 내부 전용 경로 중 <b>상태를 바꾸는 것</b>(AI 결과 수신) 전용 잠금 조회.
     * 콜백은 at-least-once라 같은 결과가 두 번 올 수 있고, 잠그지 않으면 둘 다
     * 클립 수 검사를 통과해 방송 1회당 3개 상한이 조용히 깨진다.
     */
    Optional<LiveSession> findOwnedAnyForUpdate(UUID publicId);
}
