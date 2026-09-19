package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import com.fundit.project.infrastructure.persistence.project.query.ProjectSummaryProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectInternalQueryServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectJpaRepository projectJpaRepository;
    @Mock
    private SellerProfileClient sellerProfileClient;

    @InjectMocks
    private ProjectInternalQueryService service;

    @Test
    void 존재하는_프로젝트면_판매자_ID와_공개_ID를_반환한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project project = Project.builder().id(123L).publicId(publicId).sellerId(sellerId).build();
        when(projectRepository.findById(123L)).thenReturn(Optional.of(project));

        // when
        var snapshot = service.getSnapshot(123L);

        // then
        assertThat(snapshot.sellerId()).isEqualTo(sellerId);
        assertThat(snapshot.publicId()).isEqualTo(publicId);
    }

    @Test
    void 존재하지_않는_프로젝트면_예외가_발생한다() {
        // given
        when(projectRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getSnapshot(1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 배치_요약_조회시_판매자_표시명을_함께_채운다() {
        // given
        UUID publicId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        ProjectSummaryProjection projection = new ProjectSummaryProjection() {
            public UUID getPublicId() { return publicId; }
            public UUID getSellerId() { return sellerId; }
            public String getTitle() { return "프로젝트"; }
            public String getThumbnailUrl() { return "https://cdn/x.png"; }
        };
        when(projectJpaRepository.findSummariesByPublicIdIn(List.of(publicId))).thenReturn(List.of(projection));
        when(sellerProfileClient.getDisplayName(sellerId)).thenReturn(Optional.of("메이커"));

        // when
        var result = service.getSummaries(List.of(publicId));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("프로젝트");
        assertThat(result.get(0).sellerDisplayName()).isEqualTo("메이커");
    }

    @Test
    void 빈_목록으로_배치_요약을_조회하면_리포지토리를_호출하지_않는다() {
        // when
        var result = service.getSummaries(List.of());

        // then
        assertThat(result).isEmpty();
    }
}
