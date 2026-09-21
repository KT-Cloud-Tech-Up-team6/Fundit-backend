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
                "에어쿡 프로", "가전", "주방가전", List.of("개발 동기", "제품 스펙"), 80, 5)));
        given(projectRewardClient.findRewards(projectId)).willReturn(List.of());

        // when
        AiClient.PrepareRequest product = assembler.assemble(session);

        // then
        assertThat(product.productName()).isEqualTo("에어쿡 프로");
        assertThat(product.projectPublicId()).isEqualTo(projectId.toString());
        assertThat(product.knowledge()).extracting(AiClient.KnowledgeChunk::chunkId, AiClient.KnowledgeChunk::text)
                .containsExactly(tuple("intro-0", "개발 동기"), tuple("intro-1", "제품 스펙"));
    }

    @Test
    void 프로젝트가_없으면_상품_필드는_비워서_보낸다() {
        // given — 404는 예외가 아니라 빈 값이다. 리워드만으로라도 요청은 나간다
        given(projectContextClient.find(projectId)).willReturn(Optional.empty());
        given(projectRewardClient.findRewards(projectId)).willReturn(List.of());

        // when
        AiClient.PrepareRequest product = assembler.assemble(session);

        // then
        assertThat(product.productName()).isNull();
        assertThat(product.knowledge()).isEmpty();
    }

    @Test
    void 캠페인_현황은_남은_일수를_마감일로_환산하고_달성률을_싣는다() {
        // given
        ProjectContextClient.ProjectContext context = new ProjectContextClient.ProjectContext(
                "에어쿡 프로", "가전", "주방가전", List.of(), 42, 3);

        // when
        AiClient.FundingInfo funding = AiProductContextAssembler.fundingOf(context);

        // then
        assertThat(funding.achievedRate()).isEqualTo(42);
        assertThat(funding.deadline()).isBetween(
                java.time.Instant.now().plusSeconds(3 * 86400L - 60), java.time.Instant.now().plusSeconds(3 * 86400L + 60));
    }
}
