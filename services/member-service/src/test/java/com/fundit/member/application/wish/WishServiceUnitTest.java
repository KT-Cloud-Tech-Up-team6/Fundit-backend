package com.fundit.member.application.wish;

import com.fundit.member.infrastructure.persistence.wish.WishJpaEntity;
import com.fundit.member.infrastructure.persistence.wish.WishJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishServiceUnitTest {

    @Mock
    private WishJpaRepository wishJpaRepository;

    @InjectMocks
    private WishService wishService;

    @Test
    void 찜_등록시_idempotent_insert를_호출한다() {
        // given
        UUID memberId = UUID.randomUUID();

        // when
        wishService.wish(memberId, 1L);

        // then
        verify(wishJpaRepository).insertIgnoringConflict(memberId, 1L);
    }

    @Test
    void 찜_해제시_idempotent_delete를_호출한다() {
        // given
        UUID memberId = UUID.randomUUID();

        // when
        wishService.unwish(memberId, 1L);

        // then
        verify(wishJpaRepository).deleteByMemberIdAndProjectId(memberId, 1L);
    }

    @Test
    void 찜_목록조회시_스냅샷_필드를_매핑한다() {
        // given
        UUID memberId = UUID.randomUUID();
        Instant now = Instant.now();
        WishJpaEntity entity = WishJpaEntity.builder()
                .id(1L).memberId(memberId).projectId(10L)
                .projectTitle("프로젝트A").projectThumbnailUrl("http://img").createdAt(now).build();
        Page<WishJpaEntity> page = new PageImpl<>(List.of(entity));
        when(wishJpaRepository.findByMemberId(memberId, PageRequest.of(0, 20))).thenReturn(page);

        // when
        Page<WishService.WishItem> result = wishService.getWishes(memberId, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).projectTitle()).isEqualTo("프로젝트A");
    }
}
