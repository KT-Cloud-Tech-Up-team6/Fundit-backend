package com.fundit.order.application.coupon;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.catalog.ProjectOwnershipClient;
import com.fundit.order.domain.OrderErrorCode;
import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponRepository;
import com.fundit.order.domain.coupon.CouponTargetScope;
import com.fundit.order.domain.coupon.DiscountType;
import com.fundit.order.domain.coupon.IssueChannel;
import com.fundit.order.domain.coupon.IssuerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ORDER-008 — 메이커가 자신의 프로젝트 전용 쿠폰을 발급한다. 발급주체=메이커이므로 할인분은
 * 해당 메이커 정산에서 차감된다(PaymentDomainFunctionalSpec.md PAYMENT-012 연계, 이 서비스
 * 범위 밖).
 */
@Service
@RequiredArgsConstructor
public class MakerCouponIssueService {

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final CouponRepository couponRepository;
    private final ProjectOwnershipClient projectOwnershipClient;

    @Transactional
    public Coupon issue(UUID sellerId, MakerCouponIssueCommand command) {
        UUID actualOwner = projectOwnershipClient.findSellerId(command.projectId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "존재하지 않는 프로젝트입니다."));
        if (!actualOwner.equals(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        // LIVE 쿠폰은 방송 소유자도 대조한다 — 프로젝트는 내 것인데 남의 방송에 쿠폰을 매달 수 있으면
        // 그 방송 시청자에게 내 쿠폰이 뿌려진다(확정 계약 2번: 소유권은 live가 주는 sellerId로 판정).
        if (command.issueChannel() == IssueChannel.LIVE && !sellerId.equals(command.liveSellerId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        DiscountType discountType = command.discountType();
        long discountValue = discountType == DiscountType.FREE_SHIPPING ? 0 : command.discountValue();

        validateNotObviouslyOverBudget(discountType, discountValue, command.maxDiscountAmount(),
                command.budgetLimit(), command.quantity());

        Coupon coupon = Coupon.builder()
                .couponCode(generateCouponCode())
                .couponName(command.couponName())
                .discountType(discountType)
                .discountValue(discountValue)
                .maxDiscountAmount(command.maxDiscountAmount())
                .budgetLimit(command.budgetLimit())
                .usedBudgetAmount(0)
                .issuerType(IssuerType.MAKER)
                .issuerId(sellerId)
                .targetScope(CouponTargetScope.PROJECT)
                .targetRefId(String.valueOf(command.projectId()))
                .minFundingAmount(command.minFundingAmount())
                .perMemberLimit(command.perMemberLimit())
                .remainingQuantity(command.quantity())
                .expiresAt(command.expiresAt())
                .issueChannel(command.issueChannel())
                .liveSessionId(command.liveSessionId())
                .dropType(command.dropType())
                .version(0)
                .createdAt(Instant.now())
                .build();

        return couponRepository.save(coupon);
    }

    /**
     * 발급 개수 전량이 최대 한도로 사용돼도 예산 내로 들어올 수 없는, 명백히 불가능한 경우만
     * 차단한다[가정 — OrderDomainFunctionalSpec.md(발급 즉시 차단)와 OrderDomainApiSpec.md(적용
     * 단계에서 소진 시 제외)의 서술이 서로 달라 절충함]. RATE에 max_discount_amount가 없거나
     * FREE_SHIPPING처럼 상한을 알 수 없는 경우는 검증을 건너뛴다.
     */
    private void validateNotObviouslyOverBudget(DiscountType discountType, long discountValue,
                                                 Long maxDiscountAmount, Long budgetLimit, int quantity) {
        if (budgetLimit == null) {
            return;
        }
        Long perUseMax = switch (discountType) {
            case AMOUNT -> discountValue;
            case RATE -> maxDiscountAmount;
            case FREE_SHIPPING -> null;
        };
        if (perUseMax == null) {
            return;
        }
        long theoreticalMax = perUseMax * quantity;
        if (theoreticalMax > budgetLimit) {
            throw new BusinessException(OrderErrorCode.COUPON_BUDGET_EXCEEDED);
        }
    }

    /**
     * cross-service ID 통일(#69) 이전에는 "P{projectId}-{random4}" 형태로 코드에 프로젝트 식별자를
     * 실었는데, projectId가 UUID(36자)로 바뀌면서 그 형태로는 coupon_code VARCHAR(30)을 넘긴다 —
     * 식별자를 코드에 싣지 않고 순수 랜덤 문자열로 바꾼다(충돌 위험은 랜덤 길이를 늘려 상쇄).
     */
    private String generateCouponCode() {
        StringBuilder random = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            random.append(CODE_CHARS.charAt(ThreadLocalRandom.current().nextInt(CODE_CHARS.length())));
        }
        return "MK-" + random;
    }
}
