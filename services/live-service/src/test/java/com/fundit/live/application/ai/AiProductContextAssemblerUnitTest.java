package com.fundit.live.application.ai;

import com.fundit.live.application.project.ProjectContextClient;
import com.fundit.live.application.project.ProjectRewardClient;
import com.fundit.live.domain.session.LiveSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AiProductContextAssemblerUnitTest {

    @Mock private ProjectContextClient projectContextClient;
    @Mock private ProjectRewardClient projectRewardClient;

    @InjectMocks private AiProductContextAssembler assembler;

    private final UUID projectId = UUID.randomUUID();
    private final LiveSession session = LiveSession.builder().id(1L).publicId(UUID.randomUUID()).projectId(projectId).build();

    @Test
    void 스토리_본문은_순서대로_knowledge_청크가_된다() {
        // given
        given(projectContextClient.find(projectId)).willReturn(Optional.of(new ProjectContextClient.ProjectContext(
                "에어쿡 프로", "가전", "주방가전", List.of("개발 동기", "제품 스펙"), 80, 5, null)));
        given(projectRewardClient.findRewards(projectId)).willReturn(List.of());

        // when
        AiClient.PrepareRequest product = assembler.assemble(session).orElseThrow();

        // then
        assertThat(product.productName()).isEqualTo("에어쿡 프로");
        assertThat(product.projectPublicId()).isEqualTo(projectId.toString());
        assertThat(product.knowledge()).extracting(AiClient.KnowledgeChunk::chunkId, AiClient.KnowledgeChunk::text)
                .containsExactly(tuple("intro-0", "개발 동기"), tuple("intro-1", "제품 스펙"));
    }

    @Test
    void 프로젝트가_없으면_색인_요청_자체를_만들지_않는다() {
        // given — product_category가 AI 계약상 필수라 빈 값으로 보내면 422이고,
        // 통과하더라도 빈 KB로 색인돼 모든 상품 질문이 "확인이 어렵습니다"가 된다
        given(projectContextClient.find(projectId)).willReturn(Optional.empty());

        // when & then
        assertThat(assembler.assemble(session)).isEmpty();
    }

    @Test
    void 캠페인_현황은_마감_시각을_원본_그대로_싣는다() {
        // given — "9월 15일 23시 59분 마감"처럼 시:분까지 묻는 질문이 실제로 들어온다
        java.time.Instant deadline = java.time.Instant.parse("2026-09-15T14:59:00Z");
        ProjectContextClient.ProjectContext context = new ProjectContextClient.ProjectContext(
                "에어쿡 프로", "가전", "주방가전", List.of(), 42, 3, deadline);

        // when
        AiClient.FundingInfo funding = AiProductContextAssembler.fundingOf(context);

        // then
        assertThat(funding.achievedRate()).isEqualTo(42);
        assertThat(funding.deadline()).isEqualTo(deadline);
    }

    @Test
    void 마감_시각이_없으면_남은_일수로_환산한다() {
        // given — project-service 구버전 호환용 fallback
        ProjectContextClient.ProjectContext context = new ProjectContextClient.ProjectContext(
                "에어쿡 프로", "가전", "주방가전", List.of(), 42, 3, null);

        // when
        AiClient.FundingInfo funding = AiProductContextAssembler.fundingOf(context);

        // then
        assertThat(funding.deadline()).isBetween(
                java.time.Instant.now().plusSeconds(3 * 86400L - 60), java.time.Instant.now().plusSeconds(3 * 86400L + 60));
    }
}
