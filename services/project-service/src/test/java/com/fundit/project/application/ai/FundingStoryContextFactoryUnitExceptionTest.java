package com.fundit.project.application.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.project.application.media.MediaStorageClient;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.reward.Reward;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingStoryContextFactoryUnitExceptionTest {

    @Mock
    private MediaStorageClient storageClient;

    private FundingStoryContextFactory factory;
    private final FundingStoryContextFactoryUnitTest fixtures = new FundingStoryContextFactoryUnitTest();

    @BeforeEach
    void setUp() {
        factory = new FundingStoryContextFactory(storageClient, 15L);
    }

    @Test
    void 필수_프로젝트_정보가_없으면_계약용_오류를_반환한다() {
        // given
        Project invalid = fixtures.validProject(UUID.randomUUID(), null).toBuilder().businessType(null).build();

        // when & then
        assertThatThrownBy(() -> factory.create(invalid, List.of(fixtures.reward(1L, null))))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ProjectErrorCode.INVALID_PROJECT_DATA));
        assertThatThrownBy(() -> factory.fingerprint(invalid, List.of(fixtures.reward(1L, null))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 리워드와_원본_이미지_검증에_실패하면_Core를_만들지_않는다() {
        // given & when & then — 리워드 필드 누락
        Project project = fixtures.validProject(UUID.randomUUID(), null);
        Reward invalidReward = fixtures.reward(1L, null).toBuilder().description(null).build();
        assertThatThrownBy(() -> factory.create(project, List.of(invalidReward)))
                .isInstanceOf(BusinessException.class);

        // given & when & then — 원본 이미지 확장자 검증 실패
        Project imageProject = fixtures.validProject(UUID.randomUUID(), "https://bucket.example/bad.gif");
        when(storageClient.extractKey("https://bucket.example/bad.gif"))
                .thenReturn(Optional.of("bad.gif"));
        when(storageClient.headObject("bad.gif"))
                .thenReturn(Optional.of(new MediaStorageClient.StoredObject(256L, "image/gif")));

        assertThatThrownBy(() -> factory.create(imageProject, List.of(fixtures.reward(1L, null))))
                .isInstanceOf(BusinessException.class);
    }
}
