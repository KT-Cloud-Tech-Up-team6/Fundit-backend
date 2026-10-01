package com.fundit.live.infrastructure.seed;

import com.fundit.common.error.DependencyFailureException;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveThumbnailBackfillUnitExceptionTest {

    private static final String COVER = "https://infrastudy.store/media/projects/p/cover.png";

    @Mock private LiveSessionJpaRepository sessionRepository;
    @Mock private ProjectOwnershipClient projectOwnershipClient;
    @InjectMocks private LiveThumbnailBackfill backfill;

    @Test
    void 한_프로젝트_조회가_실패해도_나머지는_채운다() {
        // given — project-service 일시 장애가 다른 LIVE 보완까지 막으면 안 된다
        UUID failing = UUID.randomUUID();
        UUID ok = UUID.randomUUID();
        LiveSessionJpaEntity okSession = LiveThumbnailBackfillUnitTest.session(ok);
        given(sessionRepository.findByThumbnailUrlIsNull())
                .willReturn(List.of(LiveThumbnailBackfillUnitTest.session(failing), okSession));
        given(projectOwnershipClient.find(failing))
                .willThrow(new DependencyFailureException(new IllegalStateException("down")));
        given(projectOwnershipClient.find(ok)).willReturn(Optional.of(new ProjectOwner(UUID.randomUUID(), COVER)));
        given(sessionRepository.fillThumbnailIfAbsent(okSession.getPublicId(), COVER)).willReturn(1);

        // when
        int filled = backfill.backfill();

        // then
        assertThat(filled).isEqualTo(1);
        verify(sessionRepository).fillThumbnailIfAbsent(okSession.getPublicId(), COVER);
    }

    @Test
    void 대상_조회가_실패해도_기동을_막지_않는다() {
        // given
        given(sessionRepository.findByThumbnailUrlIsNull()).willThrow(new IllegalStateException("db down"));

        // when & then
        assertThatCode(() -> backfill.run(null)).doesNotThrowAnyException();
    }
}
