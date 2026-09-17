package com.fundit.search.application.home;

import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectCardProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeFeedQueryServiceUnitTest {

    @Mock
    private ProjectDocumentJpaRepository projectDocumentJpaRepository;

    @InjectMocks
    private HomeFeedQueryService homeFeedQueryService;

    @Test
    void size가_없으면_기본값_20으로_조회한다() {
        // given
        when(projectDocumentJpaRepository.findByStatusAndDeletedAtIsNullOrderByParticipantCountDescWishCountDesc(
                eq(ProjectDocumentStatus.ONGOING), any())).thenReturn(List.<ProjectCardProjection>of());

        // when
        homeFeedQueryService.getHomeFeed(null);

        // then
        var captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(projectDocumentJpaRepository)
                .findByStatusAndDeletedAtIsNullOrderByParticipantCountDescWishCountDesc(
                        eq(ProjectDocumentStatus.ONGOING), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void size가_주어지면_그_값으로_조회한다() {
        // given
        when(projectDocumentJpaRepository.findByStatusAndDeletedAtIsNullOrderByParticipantCountDescWishCountDesc(
                eq(ProjectDocumentStatus.ONGOING), any())).thenReturn(List.<ProjectCardProjection>of());

        // when
        homeFeedQueryService.getHomeFeed(5);

        // then
        var captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(projectDocumentJpaRepository)
                .findByStatusAndDeletedAtIsNullOrderByParticipantCountDescWishCountDesc(
                        eq(ProjectDocumentStatus.ONGOING), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void size가_최대값을_넘으면_100으로_제한한다() {
        // given
        when(projectDocumentJpaRepository.findByStatusAndDeletedAtIsNullOrderByParticipantCountDescWishCountDesc(
                eq(ProjectDocumentStatus.ONGOING), any())).thenReturn(List.<ProjectCardProjection>of());

        // when
        homeFeedQueryService.getHomeFeed(500);

        // then
        var captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(projectDocumentJpaRepository)
                .findByStatusAndDeletedAtIsNullOrderByParticipantCountDescWishCountDesc(
                        eq(ProjectDocumentStatus.ONGOING), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }
}
