package com.fundit.project.application.project;

import com.fundit.project.infrastructure.persistence.project.ProjectJpaEntity;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerServiceUnitTest {

    @Mock
    private ProjectJpaRepository projectJpaRepository;

    @InjectMocks
    private SellerService sellerService;

    @Test
    void 공개상태_프로젝트만_과거이력에_포함한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        ProjectJpaEntity draft = ProjectJpaEntity.builder()
                .id(1L).publicId(UUID.randomUUID()).sellerId(sellerId).businessType("SOLE").status("DRAFT")
                .title("작성중").createdAt(Instant.now()).updatedAt(Instant.now()).build();
        ProjectJpaEntity succeeded = ProjectJpaEntity.builder()
                .id(2L).publicId(UUID.randomUUID()).sellerId(sellerId).businessType("SOLE").status("SUCCEEDED")
                .title("성공한프로젝트").createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectJpaRepository.findBySellerIdAndDeletedAtIsNullOrderByCreatedAtDesc(sellerId))
                .thenReturn(List.of(draft, succeeded));

        // when
        var result = sellerService.getProfile(sellerId);

        // then
        assertThat(result.businessType()).isEqualTo("SOLE");
        assertThat(result.pastProjects()).hasSize(1);
        assertThat(result.pastProjects().get(0).title()).isEqualTo("성공한프로젝트");
    }
}
