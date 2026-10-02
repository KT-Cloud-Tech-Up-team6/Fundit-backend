package com.fundit.search.infrastructure.seed;

import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaEntity;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

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
    @Mock private TransactionTemplate transactionTemplate;

    private DemoProjectStatusFixer fixer;

    @BeforeEach
    void setUp() {
        fixer = new DemoProjectStatusFixer(projectDocumentRepository, transactionTemplate);
        // 커밋까지 정상으로 끝나는 트랜잭션
        given(transactionTemplate.execute(any())).willAnswer(inv ->
                inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    private ProjectDocumentJpaEntity document(ProjectDocumentStatus status) {
        return ProjectDocumentJpaEntity.builder()
                .projectId(51L).projectPublicId(DemoProjectStatusFixer.DEMO_PROJECT_ID).status(status).build();
    }

    private void givenCloneDocument(ProjectDocumentStatus status) {
        given(projectDocumentRepository.findByProjectPublicId(DemoProjectStatusFixer.CLINPOT_CLONE_PROJECT_ID))
                .willReturn(Optional.of(ProjectDocumentJpaEntity.builder().projectId(52L)
                        .projectPublicId(DemoProjectStatusFixer.CLINPOT_CLONE_PROJECT_ID).status(status).build()));
    }

    @Test
    void 색인된_문서가_진행중이면_성립으로_맞추고_커밋_후엔_아무것도_안_한다() {
        // given — 최초 색인은 상태를 ONGOING으로 고정한다
        given(projectDocumentRepository.findByProjectPublicId(DemoProjectStatusFixer.DEMO_PROJECT_ID))
                .willReturn(Optional.of(document(ProjectDocumentStatus.ONGOING)));
        givenCloneDocument(ProjectDocumentStatus.ONGOING);

        // when
        fixer.fix();
        fixer.fix();

        // then — 바스켓·클린팟 클론 둘 다, 한 번씩만
        verify(projectDocumentRepository).updateStatus(51L, ProjectDocumentStatus.SUCCEEDED);
        verify(projectDocumentRepository).updateStatus(52L, ProjectDocumentStatus.SUCCEEDED);
        verify(projectDocumentRepository, times(2)).findByProjectPublicId(any());
    }

    @Test
    void 아직_색인되지_않았으면_다음_주기에_다시_본다() {
        // given — project 색인 이벤트는 비동기라 기동 직후엔 문서가 없을 수 있다
        given(projectDocumentRepository.findByProjectPublicId(DemoProjectStatusFixer.DEMO_PROJECT_ID))
                .willReturn(Optional.empty());
        givenCloneDocument(ProjectDocumentStatus.SUCCEEDED);

        // when
        fixer.fix();
        fixer.fix();

        // then — 색인된 클론은 첫 주기에 끝나고, 바스켓만 다시 본다
        verify(projectDocumentRepository, never()).updateStatus(anyLong(), any());
        verify(projectDocumentRepository, times(2)).findByProjectPublicId(DemoProjectStatusFixer.DEMO_PROJECT_ID);
        verify(projectDocumentRepository, times(1)).findByProjectPublicId(DemoProjectStatusFixer.CLINPOT_CLONE_PROJECT_ID);
    }
}
