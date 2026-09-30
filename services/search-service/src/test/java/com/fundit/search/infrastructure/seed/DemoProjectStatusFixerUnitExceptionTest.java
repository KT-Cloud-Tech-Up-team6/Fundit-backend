package com.fundit.search.infrastructure.seed;

import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoProjectStatusFixerUnitExceptionTest {

    @Mock private ProjectDocumentJpaRepository projectDocumentRepository;
    @Mock private TransactionTemplate transactionTemplate;

    @Test
    void 커밋에_실패하면_완료로_보지_않고_다음_주기에_다시_시도한다() {
        // given — 갱신은 했지만 커밋에서 실패했다. 플래그가 먼저 서 있으면 영영 재시도하지 않는다
        DemoProjectStatusFixer fixer = new DemoProjectStatusFixer(projectDocumentRepository, transactionTemplate);
        given(transactionTemplate.execute(any())).willThrow(new TransactionSystemException("commit failed"));

        // when
        assertThatThrownBy(fixer::fix).isInstanceOf(TransactionSystemException.class);
        assertThatThrownBy(fixer::fix).isInstanceOf(TransactionSystemException.class);

        // then
        verify(transactionTemplate, times(2)).execute(any());
    }
}
