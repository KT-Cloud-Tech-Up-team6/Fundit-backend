package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.application.media.MediaStorageClient;
import com.fundit.payment.domain.PaymentErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * F09 — 구매자 반품·교환 증빙(사진) 업로드 주소 발급. project-service 미디어 업로드 API는
 * 프로젝트 소유자(판매자)만 쓸 수 있어(project.isOwnedBy(sellerId) 검증) 구매자는 원천적으로
 * 접근할 수 없었다 — 이 서비스에 별도 경로를 둔다. 소유권 검증은 프로젝트가 아니라
 * "이 orderId가 실제로 이 구매자 것인지"(funding 소유권)를 기준으로 한다(security.md S4).
 */
@Service
public class RefundEvidenceUploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private final OrderFundingClient orderFundingClient;
    private final MediaStorageClient storageClient;
    private final Duration presignTtl;

    public RefundEvidenceUploadService(OrderFundingClient orderFundingClient, MediaStorageClient storageClient,
                                        @Value("${media.upload.presign-ttl-minutes:5}") long presignTtlMinutes) {
        this.orderFundingClient = orderFundingClient;
        this.storageClient = storageClient;
        this.presignTtl = Duration.ofMinutes(presignTtlMinutes);
    }

    public MediaStorageClient.PresignedUpload issueUploadUrl(
            UUID memberId, UUID orderId, String fileName, String contentType, long fileSize) {
        OrderFundingClient.FundingSnapshot snapshot = orderFundingClient.fetch(orderId);
        if (!memberId.equals(snapshot.memberId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        String extension = extractExtension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))
                || contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(PaymentErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        if (fileSize <= 0 || fileSize > MAX_SIZE_BYTES) {
            throw new BusinessException(PaymentErrorCode.MEDIA_TOO_LARGE);
        }

        // 클라이언트가 보낸 fileName은 키에 사용하지 않는다(추측 불가 파일명, S5) — 확장자만 재사용.
        String key = "refunds/%s/%s.%s".formatted(orderId, UUID.randomUUID(), extension.toLowerCase(Locale.ROOT));
        return storageClient.presignPut(key, contentType, presignTtl);
    }

    private String extractExtension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new BusinessException(PaymentErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        return fileName.substring(dot + 1);
    }
}
