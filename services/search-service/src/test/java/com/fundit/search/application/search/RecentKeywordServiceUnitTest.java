package com.fundit.search.application.search;

import com.fundit.search.infrastructure.persistence.recentkeyword.RecentSearchKeywordJpaEntity;
import com.fundit.search.infrastructure.persistence.recentkeyword.RecentSearchKeywordJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecentKeywordServiceUnitTest {

    @Mock
    private RecentSearchKeywordJpaRepository recentSearchKeywordJpaRepository;

    @InjectMocks
    private RecentKeywordService recentKeywordService;

    @Test
    void size가_없으면_기본값_10으로_조회한다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(recentSearchKeywordJpaRepository.findByMemberIdOrderBySearchedAtDesc(eq(memberId), any()))
                .thenReturn(List.of(RecentSearchKeywordJpaEntity.builder()
                        .memberId(memberId).keyword("무선 이어폰").searchedAt(Instant.now()).build()));

        // when
        var result = recentKeywordService.getRecentKeywords(memberId, null);

        // then
        var captor = ArgumentCaptor.forClass(Pageable.class);
        verify(recentSearchKeywordJpaRepository).findByMemberIdOrderBySearchedAtDesc(eq(memberId), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().keyword()).isEqualTo("무선 이어폰");
    }

    @Test
    void 키워드를_삭제하면_본인_행만_지운다() {
        // given
        UUID memberId = UUID.randomUUID();

        // when
        recentKeywordService.deleteKeyword(memberId, "무선 이어폰");

        // then
        verify(recentSearchKeywordJpaRepository).deleteByMemberIdAndKeyword(memberId, "무선 이어폰");
    }

    @Test
    void 전체_삭제하면_본인_행이_모두_지워진다() {
        // given
        UUID memberId = UUID.randomUUID();

        // when
        recentKeywordService.deleteAllKeywords(memberId);

        // then
        verify(recentSearchKeywordJpaRepository).deleteByMemberId(memberId);
    }
}
