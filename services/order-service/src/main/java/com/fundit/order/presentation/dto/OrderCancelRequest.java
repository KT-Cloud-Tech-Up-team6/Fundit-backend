package com.fundit.order.presentation.dto;

import com.fundit.order.domain.funding.CancelReason;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * ORDER-014 참여 취소 요청(선택) — IA v1.3 화면 {@code FL_B_MY_FUND_CL}에서 고른 사유를 저장한다.
 *
 * <p>본문 자체가 선택값이다: 사유를 보내지 않는 기존 클라이언트도 계속 취소할 수 있어야 해서
 * (FE 배포 순서와 무관하게) 컨트롤러가 {@code required = false}로 받는다.
 */
public record OrderCancelRequest(
        CancelReason cancelReason,
        @Size(max = 100, message = "취소 사유 상세는 100자 이내로 입력해 주세요.") String reasonDetail
) {

    /** 기타 사유는 무엇이 문제였는지 남지 않으면 기록으로서 의미가 없어 상세를 필수로 받는다. */
    @AssertTrue(message = "기타 사유를 선택한 경우 상세 내용을 입력해 주세요.")
    public boolean isReasonDetailPresentWhenRequired() {
        return cancelReason == null || !cancelReason.requiresDetail()
                || (reasonDetail != null && !reasonDetail.isBlank());
    }
}
