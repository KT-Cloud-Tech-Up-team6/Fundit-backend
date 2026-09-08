package com.fundit.order.application.coupon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WelcomeCouponAutoIssueServiceUnitTest {

    @Mock
    private CouponIssuanceService couponIssuanceService;

    @Test
    void 웰컴쿠폰코드가_비어있으면_자동발급을_시도하지_않는다() {
        // given
        WelcomeCouponAutoIssueService service = new WelcomeCouponAutoIssueService(couponIssuanceService, "");

        // when
        service.onMemberSignedUp(new MemberLifecycleEventListener.MemberSignedUpEvent(UUID.randomUUID()));

        // then
        verify(couponIssuanceService, never()).autoIssue(any(), any());
    }

    @Test
    void 웰컴쿠폰코드가_설정되어있으면_자동발급을_호출한다() {
        // given
        UUID memberId = UUID.randomUUID();
        WelcomeCouponAutoIssueService service = new WelcomeCouponAutoIssueService(couponIssuanceService, "WELCOME2026");
        when(couponIssuanceService.autoIssue(memberId, "WELCOME2026")).thenReturn(Optional.empty());

        // when
        service.onMemberSignedUp(new MemberLifecycleEventListener.MemberSignedUpEvent(memberId));

        // then
        verify(couponIssuanceService).autoIssue(memberId, "WELCOME2026");
    }
}
