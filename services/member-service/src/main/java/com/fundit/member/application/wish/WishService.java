package com.fundit.member.application.wish;

import com.fundit.member.infrastructure.persistence.event.WishEventOutboxJpaEntity;
import com.fundit.member.infrastructure.persistence.event.WishEventOutboxJpaRepository;
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
    private final WishEventOutboxJpaRepository wishEventOutboxJpaRepository;

    /**
     * 찜 쓰기와 <b>같은 트랜잭션</b>에서 아웃박스에 적재한다 — 찜만 커밋되고 이벤트가 사라지는
     * 경우를 없애기 위해서다. 실제 발행은 {@code WishEventOutboxWorker}가 재시도한다.
     *
     * <p>상태가 실제로 바뀐 경우에만 적재한다. 이미 찜한 프로젝트에 재요청이 오면 영향 행이 0이고,
     * 그때도 적재하면 더블탭 한 번에 이벤트가 여러 건 쌓인다.
     */
    @Transactional
    public void wish(UUID memberId, Long projectId) {
        if (wishJpaRepository.insertIgnoringConflict(memberId, projectId) > 0) {
            enqueue(WishEventOutboxJpaEntity.TYPE_WISHED, memberId, projectId);
        }
    }

    @Transactional
    public void unwish(UUID memberId, Long projectId) {
        if (wishJpaRepository.deleteByMemberIdAndProjectId(memberId, projectId) > 0) {
            enqueue(WishEventOutboxJpaEntity.TYPE_UNWISHED, memberId, projectId);
        }
    }

    @Transactional(readOnly = true)
    public Page<WishItem> getWishes(UUID memberId, Pageable pageable) {
        return wishJpaRepository.findByMemberId(memberId, pageable)
                .map(w -> new WishItem(w.getProjectId(), w.getProjectTitle(), w.getProjectThumbnailUrl(), w.getCreatedAt()));
    }

    private void enqueue(String eventType, UUID memberId, Long projectId) {
        wishEventOutboxJpaRepository.save(WishEventOutboxJpaEntity.builder()
                .eventType(eventType)
                .memberId(memberId)
                .projectId(projectId)
                .build());
    }

    public record WishItem(Long projectId, String projectTitle, String projectThumbnailUrl, Instant createdAt) {
    }
}
