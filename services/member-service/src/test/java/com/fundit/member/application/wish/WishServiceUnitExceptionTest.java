package com.fundit.member.application.wish;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.member.infrastructure.persistence.event.MemberEventOutboxJpaRepository;
import com.fundit.member.infrastructure.persistence.projectsnapshot.ProjectSnapshotJpaRepository;
import com.fundit.member.infrastructure.persistence.wish.WishJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishServiceUnitExceptionTest {

    @Mock
    private WishJpaRepository wishJpaRepository;
    @Mock
    private MemberEventOutboxJpaRepository memberEventOutboxJpaRepository;
    @Mock
    private ProjectSnapshotJpaRepository projectSnapshotJpaRepository;

    @InjectMocks
    private WishService wishService;

    @Test
    void 스냅샷이_없는_공개_id로_찜하면_NOT_FOUND이고_찜_행을_만들지_않는다() {
        // given — 승인 이벤트를 아직 받지 못한(또는 없는) 프로젝트
        UUID publicId = UUID.randomUUID();
        when(projectSnapshotJpaRepository.findProjectIdByPublicId(publicId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> wishService.wish(UUID.randomUUID(), publicId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        verifyNoInteractions(wishJpaRepository, memberEventOutboxJpaRepository);
    }

    @Test
    void 스냅샷이_없는_공개_id로_찜_여부를_조회하면_NOT_FOUND다() {
        // given
        UUID publicId = UUID.randomUUID();
        when(projectSnapshotJpaRepository.findProjectIdByPublicId(publicId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> wishService.isWished(UUID.randomUUID(), publicId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }
}
