package com.fundit.project.application.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaCategoryUnitTest {

    @Test
    void 이미지_contentType과_확장자가_일치하면_IMAGE로_판별된다() {
        // when
        var result = MediaCategory.resolve("image/png", "png");

        // then
        assertThat(result).contains(MediaCategory.IMAGE);
    }

    @Test
    void 영상_contentType과_확장자가_일치하면_VIDEO로_판별된다() {
        // when
        var result = MediaCategory.resolve("video/mp4", "mp4");

        // then
        assertThat(result).contains(MediaCategory.VIDEO);
    }

    @Test
    void contentType과_확장자가_서로_다른_카테고리면_판별되지_않는다() {
        // when
        var result = MediaCategory.resolve("image/png", "mp4");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void 화이트리스트에_없는_contentType은_판별되지_않는다() {
        // when
        var result = MediaCategory.resolve("application/octet-stream", "exe");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void 이미지_최대용량은_10MB다() {
        assertThat(MediaCategory.IMAGE.getMaxSizeBytes()).isEqualTo(10L * 1024 * 1024);
    }

    @Test
    void 영상_최대용량은_100MB다() {
        assertThat(MediaCategory.VIDEO.getMaxSizeBytes()).isEqualTo(100L * 1024 * 1024);
    }
}
