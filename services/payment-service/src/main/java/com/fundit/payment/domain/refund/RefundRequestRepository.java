package com.fundit.payment.domain.refund;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRequestRepository {

    RefundRequest save(RefundRequest refundRequest);

    Optional<RefundRequest> findById(Long id);

    /**
     * 같은 주문에 아직 처리되지 않은 발송 후 신청(하자환불·교환·반품)이 있는지. 중복 접수를 막지
     * 않으면 반품비 차감 부분취소가 두 번 실행될 수 있다.
     */
    /** 이 결제에 토스 취소를 요청했지만 아직 확정되지 않은 환불(PROCESSING + 취소 금액). 결제당 최대 1건. */
    Optional<RefundRequest> findCancelInFlightByPaymentId(UUID paymentId);

    /**
     * 취소 요청 후 {@code before}가 지나도록 확정되지 않은 환불 — 대사 배치 대상. 방금 요청한 건은 호출이
     * 진행 중일 수 있어 기준 시각으로 거른다.
     */
    List<RefundRequest> findCancelsRequestedBefore(Instant before, int limit);

    /**
     * 대사 배치가 해결하지 못한 취소 요청의 요청 시각을 {@code at}으로 미룬다 — 목록 앞자리를 계속 막지 않게 한다.
     * 아직 "취소 요청됨"인 행만 바뀐다.
     */
    void deferCancelRequest(Long id, Instant at);

    /** 토스가 거절한 즉시 취소 요청을 없앤다 — 취소가 일어나지 않았으니 환불 내역에 남기지 않는다. */
    void delete(Long id);

    boolean existsUnresolvedPostShipmentRequest(UUID fundingId);

    /**
     * 재발송 요청까지 끝나 배송을 기다리는 교환 신청(PROCESSING). 재발송분의 배송완료 이벤트로
     * 교환을 종료하기 위해 쓴다 — 펀딩당 미처리 발송 후 신청은 1건이라 최대 1건이다.
     */
    Optional<RefundRequest> findReshippingExchangeByFundingId(UUID fundingId);

    /**
     * 재발송 요청이 아직 성공하지 못한 교환 건(PROCESSING + {@code reshipment_requested_at} null).
     * 교환비는 이미 결제된 상태라 요청이 유실되면 안 되므로 워커가 이 목록을 다시 보낸다.
     */
    List<RefundRequest> findExchangesAwaitingReshipmentRequest(int limit);
}
