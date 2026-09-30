package com.fundit.member.application.wish;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.member.infrastructure.persistence.event.MemberEventOutboxJpaEntity;
import com.fundit.member.infrastructure.persistence.event.MemberEventOutboxJpaRepository;
import com.fundit.member.infrastructure.persistence.projectsnapshot.ProjectSnapshotJpaRepository;
import com.fundit.member.infrastructure.persistence.wish.WishJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WishService {

    private final WishJpaRepository wishJpaRepository;
    private final MemberEventOutboxJpaRepository memberEventOutboxJpaRepository;
    private final ProjectSnapshotJpaRepository projectSnapshotJpaRepository;

    /**
     * 찜 쓰기와 <b>같은 트랜잭션</b>에서 아웃박스에 적재한다 — 찜만 커밋되고 이벤트가 사라지는
     * 경우를 없애기 위해서다. 실제 발행은 {@code MemberEventOutboxWorker}가 재시도한다.
     *
     * <p>상태가 실제로 바뀐 경우에만 적재한다. 이미 찜한 프로젝트에 재요청이 오면 영향 행이 0이고,
     * 그때도 적재하면 더블탭 한 번에 이벤트가 여러 건 쌓인다.
     */
    @Transactional
    public void wish(UUID memberId, Long projectId) {
        if (wishJpaRepository.insertIgnoringConflict(memberId, projectId) > 0) {
            enqueue(MemberEventOutboxJpaEntity.TYPE_WISHED, memberId, projectId);
        }
    }

    @Transactional
    public void unwish(UUID memberId, Long projectId) {
        if (wishJpaRepository.deleteByMemberIdAndProjectId(memberId, projectId) > 0) {
            enqueue(MemberEventOutboxJpaEntity.TYPE_UNWISHED, memberId, projectId);
        }
    }

    /**
     * 프로젝트 상세(UUID)에서 쓰는 찜 등록·해제·여부 조회. 숫자 id로 바꾼 뒤 위 메서드를 그대로 탄다 — 멱등성과
     * "상태가 바뀔 때만 이벤트 적재" 규칙이 숫자 API와 같다. 자기 호출이라 위 메서드의 트랜잭션은 적용되지 않으므로
     * 여기서 연다.
     */
    @Transactional
    public void wish(UUID memberId, UUID projectPublicId) {
        wish(memberId, projectIdOf(projectPublicId));
    }

    @Transactional
    public void unwish(UUID memberId, UUID projectPublicId) {
        unwish(memberId, projectIdOf(projectPublicId));
    }

    @Transactional(readOnly = true)
    public boolean isWished(UUID memberId, UUID projectPublicId) {
        return wishJpaRepository.existsByMemberIdAndProjectId(memberId, projectIdOf(projectPublicId));
    }

    /** 스냅샷(승인 이벤트)이 없는 프로젝트는 찜 대상으로 보지 않는다 — 없는 공개 id로 찜 행이 생기지 않게 404. */
    private Long projectIdOf(UUID projectPublicId) {
        return projectSnapshotJpaRepository.findProjectIdByPublicId(projectPublicId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "프로젝트를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<WishItem> getWishes(UUID memberId, Pageable pageable) {
        return wishJpaRepository.findViewsByMemberId(memberId, pageable)
                .map(w -> new WishItem(w.projectId(), w.projectPublicId(), w.projectTitle(),
                        w.projectThumbnailUrl(), w.createdAt()));
    }

    private void enqueue(String eventType, UUID memberId, Long projectId) {
        memberEventOutboxJpaRepository.save(MemberEventOutboxJpaEntity.builder()
                .eventType(eventType)
                .memberId(memberId)
                .projectId(projectId)
                .build());
    }

    /** {@code projectId}(숫자)는 찜 등록·해제용, {@code projectPublicId}는 상세 조회용이다. */
    public record WishItem(Long projectId, UUID projectPublicId, String projectTitle, String projectThumbnailUrl,
                           Instant createdAt) {
    }
}
