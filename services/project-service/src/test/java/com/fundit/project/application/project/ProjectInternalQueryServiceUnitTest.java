package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectInternalQueryServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;

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
}
