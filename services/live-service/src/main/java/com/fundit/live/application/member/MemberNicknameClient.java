package com.fundit.live.application.member;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 라이브 카드의 판매자명을 member-service에서 일괄 조회한다. 없는(탈퇴 포함) 회원은 결과에서 빠진다.
 *
 * <p>카드 표시용 부가 정보라 호출 측이 실패를 잡아 닉네임만 비우고 목록은 그대로 돌려준다.
 */
public interface MemberNicknameClient {

    Map<UUID, String> findNicknames(Collection<UUID> memberIds);
}
