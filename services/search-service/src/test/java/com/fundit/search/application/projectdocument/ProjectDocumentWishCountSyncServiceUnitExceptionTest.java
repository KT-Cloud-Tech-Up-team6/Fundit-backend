package com.fundit.search.application.projectdocument;

import com.fundit.search.application.projectdocument.WishEventListener.ProjectUnwishedEvent;
import com.fundit.search.application.projectdocument.WishEventListener.ProjectWishedEvent;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectDocumentWishCountSyncServiceUnitExceptionTest {

    @Mock
    private ProjectDocumentJpaRepository projectDocumentJpaRepository;

    @InjectMocks
    private ProjectDocumentWishCountSyncService service;

    @Test
    void 색인이_없으면_찜_가드를_쓰기_전에_재시도_예외를_던진다() {
        // given
        when(projectDocumentJpaRepository.existsById(1L)).thenReturn(false);
        var event = new ProjectWishedEvent(1L, UUID.randomUUID());

        // when
        Throwable thrown = catchThrowable(() -> service.onProjectWished(event));

        // then
        assertThat(thrown).isInstanceOf(SearchIndexNotReadyException.class);
        verify(projectDocumentJpaRepository, never()).insertWishMemberIfAbsent(event.projectId(), event.memberId());
    }

    @Test
    void 색인이_없으면_찜_해제_가드를_쓰기_전에_재시도_예외를_던진다() {
        // given
        when(projectDocumentJpaRepository.existsById(1L)).thenReturn(false);
        var event = new ProjectUnwishedEvent(1L, UUID.randomUUID());

        // when
        Throwable thrown = catchThrowable(() -> service.onProjectUnwished(event));

        // then
        assertThat(thrown).isInstanceOf(SearchIndexNotReadyException.class);
        verify(projectDocumentJpaRepository, never()).deleteWishMember(event.projectId(), event.memberId());
    }
}
