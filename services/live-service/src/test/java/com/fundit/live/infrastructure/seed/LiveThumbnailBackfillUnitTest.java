package com.fundit.live.infrastructure.seed;

import com.fundit.live.application.project.ProjectOwnershipClient;
import com.fundit.live.application.project.ProjectOwnershipClient.ProjectOwner;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveThumbnailBackfillUnitTest {

    private static final String COVER = "https://infrastudy.store/media/projects/p/cover.png";

    @Mock private LiveSessionJpaRepository sessionRepository;
    @Mock private ProjectOwnershipClient projectOwnershipClient;
    @InjectMocks private LiveThumbnailBackfill backfill;

    @Test
    void 비어_있는_썸네일을_프로젝트_대표_이미지로_채우고_프로젝트는_한_번만_조회한다() {
        // given — 같은 프로젝트로 LIVE를 여러 번 만들 수 있다
        UUID projectId = UUID.randomUUID();
        LiveSessionJpaEntity first = session(projectId);
        LiveSessionJpaEntity second = session(projectId);
        given(sessionRepository.findByThumbnailUrlIsNull()).willReturn(List.of(first, second));
        given(projectOwnershipClient.find(projectId))
                .willReturn(Optional.of(new ProjectOwner(UUID.randomUUID(), COVER)));
        given(sessionRepository.fillThumbnailIfAbsent(any(), any())).willReturn(1);

        // when
        int filled = backfill.backfill();

        // then
        assertThat(filled).isEqualTo(2);
        verify(projectOwnershipClient, times(1)).find(projectId);
        verify(sessionRepository).fillThumbnailIfAbsent(first.getPublicId(), COVER);
        verify(sessionRepository).fillThumbnailIfAbsent(second.getPublicId(), COVER);
    }

    @Test
    void 대표_이미지가_없거나_프로젝트가_없으면_건너뛴다() {
        // given
        UUID noCover = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        given(sessionRepository.findByThumbnailUrlIsNull()).willReturn(List.of(session(noCover), session(missing)));
        given(projectOwnershipClient.find(noCover)).willReturn(Optional.of(new ProjectOwner(UUID.randomUUID(), null)));
        given(projectOwnershipClient.find(missing)).willReturn(Optional.empty());

        // when
        int filled = backfill.backfill();

        // then
        assertThat(filled).isZero();
        verify(sessionRepository, never()).fillThumbnailIfAbsent(any(), any());
    }

    static LiveSessionJpaEntity session(UUID projectId) {
        return LiveSessionJpaEntity.builder().publicId(UUID.randomUUID()).projectId(projectId).build();
    }
}
