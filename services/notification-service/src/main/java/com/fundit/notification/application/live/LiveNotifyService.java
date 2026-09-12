package com.fundit.notification.application.live;

import com.fundit.notification.infrastructure.persistence.livenotifyrequest.LiveNotifyRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * LIVE 시작 알림 신청/해제(NOTI-002). 신청 목록은 LIVE 시작 시 LIVE_START 알림의 수신자가 된다.
 * 신청·해제 모두 idempotent하다 — 중복 요청·네트워크 재시도를 실패로 처리하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class LiveNotifyService {

    private final LiveNotifyRequestJpaRepository liveNotifyRequestJpaRepository;

    @Transactional
    public void requestNotify(UUID liveId, UUID memberId) {
        liveNotifyRequestJpaRepository.insertIgnoringConflict(liveId, memberId);
    }

    @Transactional
    public void cancelNotify(UUID liveId, UUID memberId) {
        liveNotifyRequestJpaRepository.deleteByLiveIdAndMemberId(liveId, memberId);
    }
}
