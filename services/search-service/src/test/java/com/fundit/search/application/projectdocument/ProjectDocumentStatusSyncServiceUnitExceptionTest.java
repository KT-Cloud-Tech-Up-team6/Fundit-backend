package com.fundit.search.application.projectdocument;

import com.fundit.search.application.projectdocument.FundingStatusEventListener.FundingSucceededEvent;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectDocumentStatusSyncServiceUnitExceptionTest {

    @Mock
    private ProjectDocumentJpaRepository projectDocumentJpaRepository;

    @InjectMocks
    private ProjectDocumentStatusSyncService service;

    @Test
    void 색인이_없으면_상태_이벤트를_재시도_예외로_보류한다() {
        // given
        when(projectDocumentJpaRepository.updateStatus(999L, ProjectDocumentStatus.SUCCEEDED)).thenReturn(0);

        // when
        Throwable thrown = catchThrowable(() -> service.onFundingSucceeded(new FundingSucceededEvent(1L, 999L)));

        // then
        assertThat(thrown).isInstanceOf(SearchIndexNotReadyException.class);
    }
}
