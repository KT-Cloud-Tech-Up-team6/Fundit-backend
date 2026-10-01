package com.fundit.search.infrastructure.seed;

import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaEntity;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoFundingStatsFixerUnitTest {

    /** project MockProjectSeeder 시드 1번 프로젝트 — 양쪽 json이 같은 publicId를 들고 있어야 보정이 맞는다. */
    private static final UUID FIRST_MOCK_PROJECT = UUID.fromString("80f88d7e-089a-5226-b306-9e7168853e60");
    private static final int MOCK_PROJECT_COUNT = 50;

    @Mock private ProjectDocumentJpaRepository projectDocumentRepository;
    @Mock private TransactionTemplate transactionTemplate;

    private DemoFundingStatsFixer fixer;

    @BeforeEach
    void setUp() {
        fixer = new DemoFundingStatsFixer(projectDocumentRepository, transactionTemplate, JsonMapper.builder().build());
        given(transactionTemplate.execute(any())).willAnswer(inv ->
                inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    private ProjectDocumentJpaEntity document(long currentAmount) {
        return ProjectDocumentJpaEntity.builder().projectId(1L).currentAmount(currentAmount).build();
    }

    @Test
    void 통계가_0인_색인은_시드값으로_보정하고_전부_끝나면_다시_안_본다() {
        // given — 시드 50건 전부 색인된 상태
        given(projectDocumentRepository.findByProjectPublicId(any())).willReturn(Optional.of(document(0L)));
        given(projectDocumentRepository.updateFundingStatsIfUnset(any(), anyLong(), any())).willReturn(1);

        // when
        fixer.fix();
        fixer.fix();

        // then
        verify(projectDocumentRepository).updateFundingStatsIfUnset(FIRST_MOCK_PROJECT, 190_000L, 9);
        verify(projectDocumentRepository, times(MOCK_PROJECT_COUNT)).findByProjectPublicId(any());
    }

    @Test
    void 이미_통계가_있는_색인은_건드리지_않는다() {
        // given — 실제 order 배치 이벤트가 이미 반영된 행
        given(projectDocumentRepository.findByProjectPublicId(any())).willReturn(Optional.of(document(500_000L)));

        // when
        fixer.fix();

        // then
        verify(projectDocumentRepository, never()).updateFundingStatsIfUnset(any(), anyLong(), anyInt());
    }

    @Test
    void 아직_색인되지_않았으면_다음_주기에_다시_본다() {
        // given — 색인은 project 이벤트로 비동기로 들어온다
        given(projectDocumentRepository.findByProjectPublicId(any())).willReturn(Optional.empty());

        // when
        fixer.fix();
        fixer.fix();

        // then
        verify(projectDocumentRepository, times(MOCK_PROJECT_COUNT * 2)).findByProjectPublicId(any());
    }
}
