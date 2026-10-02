package com.fundit.project.application.media;

import com.fundit.common.error.BusinessException;
import com.fundit.project.domain.ProjectErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 기존 저장 API(프로젝트 스토리, 리워드)가 fileUrl을 저장하기 전에 재사용하는 공용 검증기.
 * MediaUploadService(업로드 주소 발급)를 거치지 않은 URL이 그대로 저장되지 않도록 막는다.
 * 소유권은 호출부(ProjectService/RewardService의 기존 loadOwned)가 이미 검증했다고 전제하고
 * 여기서는 재검증하지 않는다 — 경로/실존/크기, 이미지는 실제 형식까지 확인한다.
 *
 * <p>이미지 실제 형식(#224): 업로드는 클라이언트가 S3에 직접 PUT해 BE가 바이트를 보지 못한다. 그래서 JPEG 바이트를
 * {@code image/png}로 올려도 저장되고, AI에 넘어간 뒤에야 거부됐다. 저장 시 앞 12바이트만 읽어 매직 바이트와
 * 저장된 MIME을 비교한다.
 */
@Component
@RequiredArgsConstructor
public class MediaUrlValidator {

    private static final int SIGNATURE_LENGTH = 12;

    private final MediaStorageClient storageClient;

    /** 검증을 통과한 객체 — 호출부가 키·MIME·크기를 다시 조회하지 않게 돌려준다. */
    public record ValidatedMedia(String key, MediaStorageClient.StoredObject stored) {
    }

    public void validate(UUID projectPublicId, String fileUrl, MediaCategory category) {
        if (category == MediaCategory.IMAGE) {
            validateImage(projectPublicId, fileUrl);
            return;
        }
        validateStored(projectPublicId, fileUrl, category);
    }

    /** 경로·실존·크기에 더해 실제 바이트 형식이 저장된 MIME과 같은지 확인한다. */
    public ValidatedMedia validateImage(UUID projectPublicId, String fileUrl) {
        ValidatedMedia media = validateStored(projectPublicId, fileUrl, MediaCategory.IMAGE);
        String declared = normalize(media.stored().contentType());
        String actual = detectImageType(storageClient.readPrefix(media.key(), SIGNATURE_LENGTH)).orElse(null);
        if (actual == null || !actual.equals(declared)) {
            throw new BusinessException(ProjectErrorCode.MEDIA_TYPE_MISMATCH);
        }
        return media;
    }

    private ValidatedMedia validateStored(UUID projectPublicId, String fileUrl, MediaCategory category) {
        String key = storageClient.extractKey(fileUrl)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.INVALID_MEDIA_URL));

        // 기존 S3 형식 URL은 접두사 없는 키로 추출된다(그 객체는 실제로 media/ 밖에 있다) — 둘 다 받는다.
        String projectPrefix = "projects/" + projectPublicId + "/";
        if (!key.startsWith(MediaStorageClient.KEY_PREFIX + projectPrefix) && !key.startsWith(projectPrefix)) {
            throw new BusinessException(ProjectErrorCode.INVALID_MEDIA_URL);
        }

        MediaStorageClient.StoredObject stored = storageClient.headObject(key)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.INVALID_MEDIA_URL));
        if (stored.contentLength() > category.getMaxSizeBytes()) {
            throw new BusinessException(ProjectErrorCode.MEDIA_TOO_LARGE);
        }
        return new ValidatedMedia(key, stored);
    }

    /** JPEG {@code FF D8 FF} / PNG {@code 89 50 4E 47 0D 0A 1A 0A} / WebP {@code RIFF????WEBP}. */
    static Optional<String> detectImageType(byte[] head) {
        if (startsWith(head, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(head, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("image/png");
        }
        if (startsWith(head, 0, 'R', 'I', 'F', 'F') && startsWith(head, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] head, int offset, int... expected) {
        if (head == null || head.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /** {@code image/jpg}는 비표준이지만 브라우저가 보내기도 해서 jpeg로 본다. */
    private static String normalize(String contentType) {
        if (contentType == null) {
            return null;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.equals("image/jpg") ? "image/jpeg" : lower;
    }
}
