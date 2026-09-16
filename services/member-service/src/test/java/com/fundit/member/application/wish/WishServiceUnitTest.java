package com.fundit.member.application.wish;

import com.fundit.member.infrastructure.persistence.event.MemberEventOutboxJpaEntity;
import com.fundit.member.infrastructure.persistence.event.MemberEventOutboxJpaRepository;
import com.fundit.member.infrastructure.persistence.wish.WishJpaEntity;
import com.fundit.member.infrastructure.persistence.wish.WishJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishServiceUnitTest {

    @Mock
    private WishJpaRepository wishJpaRepository;
    @Mock
    private MemberEventOutboxJpaRepository memberEventOutboxJpaRepository;

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
    void 찜이_실제로_등록되면_아웃박스에_적재한다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(wishJpaRepository.insertIgnoringConflict(memberId, 1L)).thenReturn(1);

        // when
        wishService.wish(memberId, 1L);

        // then
        ArgumentCaptor<MemberEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(MemberEventOutboxJpaEntity.class);
        verify(memberEventOutboxJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(MemberEventOutboxJpaEntity.TYPE_WISHED);
        assertThat(captor.getValue().getMemberId()).isEqualTo(memberId);
        assertThat(captor.getValue().getProjectId()).isEqualTo(1L);
    }

    @Test
    void 찜이_실제로_해제되면_아웃박스에_적재한다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(wishJpaRepository.deleteByMemberIdAndProjectId(memberId, 1L)).thenReturn(1);

        // when
        wishService.unwish(memberId, 1L);

        // then
        ArgumentCaptor<MemberEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(MemberEventOutboxJpaEntity.class);
        verify(memberEventOutboxJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(MemberEventOutboxJpaEntity.TYPE_UNWISHED);
    }

    /** 하트 더블탭 한 번에 이벤트가 여러 건 쌓이면 소비 측이 멱등이어도 아웃박스만 불어난다. */
    @Test
    void 이미_찜한_프로젝트면_아웃박스에_적재하지_않는다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(wishJpaRepository.insertIgnoringConflict(memberId, 1L)).thenReturn(0);

        // when
        wishService.wish(memberId, 1L);

        // then
        verify(memberEventOutboxJpaRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 찜하지_않은_프로젝트를_해제하면_아웃박스에_적재하지_않는다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(wishJpaRepository.deleteByMemberIdAndProjectId(memberId, 1L)).thenReturn(0);

        // when
        wishService.unwish(memberId, 1L);

        // then
        verify(memberEventOutboxJpaRepository, never()).save(org.mockito.ArgumentMatchers.any());
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
