package com.fundit.payment.infrastructure.toss;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.payment.application.payment.TossApiException;
import com.fundit.payment.application.payment.TossPaymentsClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

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
        } catch (HttpServerErrorException e) {
            // 5xx는 "거절"이 아니라 "결과 불명"이다 — 토스에서는 승인됐는데 응답만 실패했을 수 있어
            // 결제를 FAILED로 굳히지 않고 PENDING으로 둔다(재확정 시 lookup으로 대조).
            throw new DependencyFailureException(e);
        } catch (RestClientResponseException e) {
            throw toTossApiException(e);
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    @Override
    public TossPaymentLookup lookup(String paymentKey) {
        try {
            TossPaymentResponse response = tossPaymentsRestClient.get()
                    .uri("/v1/payments/{paymentKey}", paymentKey)
                    .retrieve()
                    .body(TossPaymentResponse.class);
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("토스 결제 조회 응답 본문이 없습니다."));
            }
            return new TossPaymentLookup(response.status(), toResult(response));
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    @Override
    public Optional<TossPaymentLookup> lookupByOrderId(String orderId) {
        try {
            TossPaymentResponse response = tossPaymentsRestClient.get()
                    .uri("/v1/payments/orders/{orderId}", orderId)
                    .retrieve()
                    .body(TossPaymentResponse.class);
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("토스 결제 조회 응답 본문이 없습니다."));
            }
            return Optional.of(new TossPaymentLookup(response.status(), toResult(response)));
        } catch (HttpClientErrorException.NotFound e) {
            // 결제 인증 전이라 토스에 결제 자체가 없다 — 승인될 수 없는 상태다. 코드가 다른 404(경로 오류 등)를
            // "결제 없음"으로 믿으면 승인된 결제를 FAILED로 닫을 수 있어 의존성 실패로 둔다.
            if (TossApiException.NOT_FOUND_PAYMENT.equals(toTossApiException(e).getTossErrorCode())) {
                return Optional.empty();
            }
            throw new DependencyFailureException(e);
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
