package com.fundit.project.domain.project;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectUnitTest {

    private Project draftProject() {
        return Project.builder()
                .id(1L)
                .publicId(UUID.randomUUID())
                .sellerId(UUID.randomUUID())
                .status(ProjectStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void 본인_소유_여부를_판별한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Project project = draftProject().toBuilder().sellerId(sellerId).build();

        // when & then
        assertThat(project.isOwnedBy(sellerId)).isTrue();
        assertThat(project.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Nested
    class 공개여부_판별 {

        @Test
        void ONGOING_SUCCEEDED_FAILED는_공개상태다() {
            assertThat(draftProject().toBuilder().status(ProjectStatus.ONGOING).build().isPublic()).isTrue();
            assertThat(draftProject().toBuilder().status(ProjectStatus.SUCCEEDED).build().isPublic()).isTrue();
            assertThat(draftProject().toBuilder().status(ProjectStatus.FAILED).build().isPublic()).isTrue();
        }

        @Test
        void DRAFT는_비공개상태다() {
            assertThat(draftProject().isPublic()).isFalse();
        }
    }

    @Nested
    class 기본정보_수정 {

        @Test
        void 전달된_필드만_갱신한다() {
            // given
            Project project = draftProject().toBuilder().title("기존 제목").goalAmount(1_000_000L).build();

            // when
            project.updateBasicInfo(null, null, null, "새 제목", null);

            // then
            assertThat(project.getTitle()).isEqualTo("새 제목");
            assertThat(project.getGoalAmount()).isEqualTo(1_000_000L);
        }

        @Test
        void 모든_필수항목이_채워지면_완료로_판단한다() {
            // given
            Project project = draftProject();

            // when
            project.updateBasicInfo(BusinessType.SOLE, "테크·가전", "생활가전", "제목", 1_000_000L);

            // then
            assertThat(project.hasCompletedBasicInfo()).isTrue();
        }

        @Test
        void 일부만_채워지면_미완료로_판단한다() {
            // given
            Project project = draftProject();

            // when
            project.updateBasicInfo(BusinessType.SOLE, null, null, "제목", null);

            // then
            assertThat(project.hasCompletedBasicInfo()).isFalse();
        }
    }

    @Nested
    class 소개_수정 {

        @Test
        void 전달된_필드만_갱신한다() {
            // given
            Project project = draftProject().toBuilder().coverImageUrl("old.jpg").build();
            List<IntroContentBlock> content = List.of(new IntroContentBlock(IntroContentType.TEXT, "hello"));

            // when
            project.updateStory(null, null, content);

            // then
            assertThat(project.getCoverImageUrl()).isEqualTo("old.jpg");
            assertThat(project.hasStory()).isTrue();
        }

        @Test
        void 소개콘텐츠가_없으면_미완료로_판단한다() {
            // given
            Project project = draftProject();

            // when & then
            assertThat(project.hasStory()).isFalse();
        }
    }

    @Nested
    class 삭제 {

        @Test
        void DRAFT면_소프트삭제된다() {
            // given
            Project project = draftProject();

            // when
            project.delete();

            // then
            assertThat(project.isDeleted()).isTrue();
        }
    }

    @Nested
    class 공개_전환 {

        @Test
        void 필수항목이_완료된_DRAFT면_바로_ONGOING으로_전환되고_펀딩기간이_확정된다() {
            // given
            Project project = draftProject().toBuilder()
                    .businessType(BusinessType.SOLE)
                    .categoryMajor("테크·가전").categoryMinor("생활가전")
                    .title("제목").goalAmount(1_000_000L)
                    .introContent(List.of(new IntroContentBlock(IntroContentType.TEXT, "본문")))
                    .build();
            Instant start = Instant.now();
            Instant deadline = start.plusSeconds(60);

            // when
            project.publish(start, deadline);

            // then
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ONGOING);
            assertThat(project.getFundingStartAt()).isEqualTo(start);
            assertThat(project.getFundingDeadline()).isEqualTo(deadline);
        }
    }
}
