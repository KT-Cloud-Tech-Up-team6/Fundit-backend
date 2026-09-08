package com.fundit.order.infrastructure.persistence.coupon;

import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CouponPersistenceAdapter implements CouponRepository {

    private static final Logger log = LoggerFactory.getLogger(CouponPersistenceAdapter.class);
    private static final int MAX_RETRY = 3;

    private final CouponJpaRepository jpaRepository;
    private final CouponMapper mapper;

    @Override
    public Optional<Coupon> findByCouponCode(String couponCode) {
        return jpaRepository.findByCouponCode(couponCode).map(mapper::toDomain);
    }

    @Override
    public List<Coupon> findByCouponCodeIn(Collection<String> couponCodes) {
        return jpaRepository.findByCouponCodeIn(couponCodes).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Coupon save(Coupon coupon) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(coupon)));
    }

    // InventoryPersistenceAdapter와 동일한 이유로 트랜잭션 경계를 어댑터가 직접 보장한다
    // (@Modifying 쿼리의 flush()는 활성 트랜잭션이 필요 — 호출부에 의존하지 않는다).
    @Override
    @Transactional
    public boolean decreaseRemainingQuantity(String couponCode) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            Optional<CouponJpaEntity> currentOpt = jpaRepository.findByCouponCode(couponCode);
            if (currentOpt.isEmpty() || currentOpt.get().getRemainingQuantity() <= 0) {
                return false;
            }
            CouponJpaEntity current = currentOpt.get();
            int updated = jpaRepository.decreaseRemainingQuantity(couponCode, current.getVersion());
            if (updated > 0) {
                return true;
            }
            log.debug("쿠폰 수량 차감 버전 충돌, 재시도. couponCode={}, attempt={}", couponCode, attempt + 1);
        }
        return false;
    }

    @Override
    @Transactional
    public boolean increaseUsedBudget(String couponCode, long amount) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            Optional<CouponJpaEntity> currentOpt = jpaRepository.findByCouponCode(couponCode);
            if (currentOpt.isEmpty()) {
                return false;
            }
            CouponJpaEntity current = currentOpt.get();
            int updated = jpaRepository.increaseUsedBudget(couponCode, amount, current.getVersion());
            if (updated > 0) {
                return true;
            }
            log.debug("쿠폰 예산 반영 버전 충돌, 재시도. couponCode={}, attempt={}", couponCode, attempt + 1);
        }
        log.error("쿠폰 예산 반영 재시도 소진 — 운영 확인 필요. couponCode={}, amount={}", couponCode, amount);
        return false;
    }
}
