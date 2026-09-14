package com.fundit.project.application.media;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 업로드 허용 정책(할일 D 확정 스펙) — 프레임워크 의존 없는 순수 로직이라 domain이 아닌
 * application에 둔다(정책값 자체가 도메인 불변식이 아니라 업로드 유스케이스 전용 규칙이라서).
 * 이미지 10MB(10,485,760 bytes), 영상 100MB(104,857,600 bytes, 1MB=1,048,576 기준)로 고정됐다.
 */
public enum MediaCategory {

    IMAGE(List.of("jpg", "jpeg", "png", "webp"),
            List.of("image/jpeg", "image/png", "image/webp"),
            10L * 1024 * 1024),
    VIDEO(List.of("mp4"),
            List.of("video/mp4"),
            100L * 1024 * 1024);

    private final List<String> allowedExtensions;
    private final List<String> allowedContentTypes;
    private final long maxSizeBytes;

    MediaCategory(List<String> allowedExtensions, List<String> allowedContentTypes, long maxSizeBytes) {
        this.allowedExtensions = allowedExtensions;
        this.allowedContentTypes = allowedContentTypes;
        this.maxSizeBytes = maxSizeBytes;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public boolean supportsContentType(String contentType) {
        return contentType != null && allowedContentTypes.contains(contentType.toLowerCase(Locale.ROOT));
    }

    public boolean supportsExtension(String extension) {
        return extension != null && allowedExtensions.contains(extension.toLowerCase(Locale.ROOT));
    }

    /** contentType과 확장자가 둘 다 같은 카테고리의 화이트리스트에 있어야 매칭된다(둘 중 하나만 위조 방지, S5). */
    public static Optional<MediaCategory> resolve(String contentType, String extension) {
        return Arrays.stream(values())
                .filter(category -> category.supportsContentType(contentType) && category.supportsExtension(extension))
                .findFirst();
    }
}
