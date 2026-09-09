package com.fundit.payment.infrastructure.toss;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.payment.application.payment.TossApiException;
import com.fundit.payment.application.payment.TossPaymentsClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 토스페이먼츠 결제위젯 승인/취소 API 연동 구현체.
 * 참고: https://docs.tosspayments.com/reference (결제 승인 POST /v1/payments/confirm,
 * 결제 취소 POST /v1/payments/{paymentKey}/cancel)
 */
@Component
@RequiredArgsConstructor
public class TossPaymentsHttpClient implements TossPaymentsClient {

    private static final Logger log = LoggerFactory.getLogger(TossPaymentsHttpClient.class);

    private final RestClient tossPaymentsRestClient;

    @Override
    public TossPaymentResult confirm(String paymentKey, String orderId, long amount) {
        try {
            TossPaymentResponse response = tossPaymentsRestClient.post()
                    .uri("/v1/payments/confirm")
                    .body(new ConfirmRequest(paymentKey, orderId, amount))
                    .retrieve()
                    .body(TossPaymentResponse.class);
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("토스 결제 승인 응답 본문이 없습니다."));
            }
            return toResult(response);
        } catch (RestClientResponseException e) {
            throw toTossApiException(e);
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    @Override
    public TossCancelResult cancel(String paymentKey, long cancelAmount, String cancelReason) {
        try {
            TossPaymentResponse response = tossPaymentsRestClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                    .body(new CancelRequest(cancelReason, cancelAmount))
                    .retrieve()
                    .body(TossPaymentResponse.class);
            if (response == null || response.cancels() == null || response.cancels().isEmpty()) {
                throw new DependencyFailureException(new IllegalStateException("토스 결제 취소 응답에 취소 내역이 없습니다."));
            }
            // 이번 호출로 생긴 취소 건은 cancels 배열의 마지막 원소다(과거 부분취소 이력이 앞에 쌓여 있을 수 있음).
            CancelDetail latest = response.cancels().get(response.cancels().size() - 1);
            return new TossCancelResult(latest.transactionKey(), parseInstant(latest.canceledAt()), latest.cancelAmount());
        } catch (RestClientResponseException e) {
            throw toTossApiException(e);
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private TossPaymentResult toResult(TossPaymentResponse response) {
        String easyPayProvider = response.easyPay() == null ? null : response.easyPay().provider();
        return new TossPaymentResult(response.paymentKey(), response.orderId(), response.secret(),
                response.method(), easyPayProvider, parseInstant(response.approvedAt()), response.totalAmount());
    }

    private Instant parseInstant(String isoOffsetDateTime) {
        return isoOffsetDateTime == null ? null : OffsetDateTime.parse(isoOffsetDateTime).toInstant();
    }

    private TossApiException toTossApiException(RestClientResponseException e) {
        try {
            TossErrorResponse error = e.getResponseBodyAs(TossErrorResponse.class);
            if (error != null && error.code() != null) {
                return new TossApiException(error.code(), error.message());
            }
        } catch (RuntimeException parseError) {
            log.warn("토스 에러 응답 파싱 실패, 원본 상태코드로 대체합니다.", parseError);
        }
        return new TossApiException("UNKNOWN_ERROR", e.getMessage());
    }

    private record ConfirmRequest(String paymentKey, String orderId, long amount) {
    }

    private record CancelRequest(String cancelReason, long cancelAmount) {
    }

    private record TossErrorResponse(String code, String message) {
    }

    private record TossPaymentResponse(String paymentKey, String orderId, String status, String method,
                                        EasyPay easyPay, String approvedAt, long totalAmount, String secret,
                                        List<CancelDetail> cancels) {
    }

    private record EasyPay(String provider) {
    }

    private record CancelDetail(String transactionKey, long cancelAmount, String canceledAt) {
    }
}
