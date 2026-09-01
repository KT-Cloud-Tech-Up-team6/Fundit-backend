package com.fundit.project.application.engagement;

import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.domain.project.Project;
import com.fundit.project.infrastructure.persistence.engagement.OpenNotifyRequestJpaRepository;
import com.fundit.project.infrastructure.persistence.engagement.ProjectFollowJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * PROJECT-025/026. 팔로우와 오픈알림신청 모두 (project_id, member_id) 유니크라
 * 중복 요청은 새로 만들지 않고 그대로 성공 처리한다(idempotent).
 * 중복 판정은 조회로 걸러내지 않고 INSERT 시점에 DB가 하도록 맡긴다 — 동시 요청 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EngagementService {

    private final ProjectFollowJpaRepository followJpaRepository;
    private final OpenNotifyRequestJpaRepository openNotifyJpaRepository;
    private final ProjectAccessGuard accessGuard;
    private final CurrentUserProvider currentUserProvider;

    public void follow(UUID projectId) {
        var currentUser = currentUserProvider.require();
        Project project = accessGuard.findVisible(projectId, currentUser.id());
        followJpaRepository.insertIfAbsent(project.getId(), currentUser.id());
    }

    public void unfollow(UUID projectId) {
        var currentUser = currentUserProvider.require();
        Project project = accessGuard.findVisible(projectId, currentUser.id());
        followJpaRepository.deleteByProjectIdAndMemberId(project.getId(), currentUser.id());
    }

    public void requestOpenNotify(UUID projectId) {
        var currentUser = currentUserProvider.require();
        Project project = accessGuard.findVisible(projectId, currentUser.id());
        openNotifyJpaRepository.insertIfAbsent(project.getId(), currentUser.id());
    }

    public void cancelOpenNotify(UUID projectId) {
        var currentUser = currentUserProvider.require();
        Project project = accessGuard.findVisible(projectId, currentUser.id());
        openNotifyJpaRepository.deleteByProjectIdAndMemberId(project.getId(), currentUser.id());
    }
}
