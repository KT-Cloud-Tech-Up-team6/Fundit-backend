package com.fundit.project.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** 회원 프로필은 member-service 소관이라, 응답에 노출할 닉네임·찜 수만 조회해 병합한다. */
public interface MemberPort {

    Map<UUID, String> findNicknames(Collection<UUID> memberIds);

    long countWishes(Long projectId);
}
