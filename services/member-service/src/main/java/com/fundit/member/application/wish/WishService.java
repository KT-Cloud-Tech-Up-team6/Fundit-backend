package com.fundit.member.application.wish;

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

    @Transactional
    public void wish(UUID memberId, Long projectId) {
        wishJpaRepository.insertIgnoringConflict(memberId, projectId);
    }

    @Transactional
    public void unwish(UUID memberId, Long projectId) {
        wishJpaRepository.deleteByMemberIdAndProjectId(memberId, projectId);
    }

    @Transactional(readOnly = true)
    public Page<WishItem> getWishes(UUID memberId, Pageable pageable) {
        return wishJpaRepository.findByMemberId(memberId, pageable)
                .map(w -> new WishItem(w.getProjectId(), w.getProjectTitle(), w.getProjectThumbnailUrl(), w.getCreatedAt()));
    }

    public record WishItem(Long projectId, String projectTitle, String projectThumbnailUrl, Instant createdAt) {
    }
}
