package com.fundit.search.infrastructure.seed;

import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaEntity;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoProjectStatusFixerUnitTest {

    @Mock private ProjectDocumentJpaRepository projectDocumentRepository;

    @InjectMocks private DemoProjectStatusFixer fixer;

    private ProjectDocumentJpaEntity document(ProjectDocumentStatus status) {
        return ProjectDocumentJpaEntity.builder()
                .projectId(51L).projectPublicId(DemoProjectStatusFixer.DEMO_PROJECT_ID).status(status).build();
    }

    @Test
    void 색인된_문서가_진행중이면_성립으로_맞추고_이후엔_아무것도_안_한다() {
        // given — 최초 색인은 상태를 ONGOING으로 고정한다
        given(projectDocumentRepository.findByProjectPublicId(DemoProjectStatusFixer.DEMO_PROJECT_ID))
                .willReturn(Optional.of(document(ProjectDocumentStatus.ONGOING)));

        // when
        fixer.fix();
        fixer.fix();

        // then
        verify(projectDocumentRepository).updateStatus(51L, ProjectDocumentStatus.SUCCEEDED);
        verify(projectDocumentRepository, times(1)).findByProjectPublicId(any());
    }

    @Test
    void 아직_색인되지_않았으면_다음_주기에_다시_본다() {
        // given — project 색인 이벤트는 비동기라 기동 직후엔 문서가 없을 수 있다
        given(projectDocumentRepository.findByProjectPublicId(DemoProjectStatusFixer.DEMO_PROJECT_ID))
                .willReturn(Optional.empty());

        // when
        fixer.fix();
        fixer.fix();

        // then
        verify(projectDocumentRepository, never()).updateStatus(anyLong(), any());
        verify(projectDocumentRepository, times(2)).findByProjectPublicId(any());
    }
}
