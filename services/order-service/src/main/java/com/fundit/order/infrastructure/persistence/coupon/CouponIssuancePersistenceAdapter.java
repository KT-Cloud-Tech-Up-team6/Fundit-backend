package com.fundit.order.infrastructure.persistence.coupon;

import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponIssuanceRepository;
import com.fundit.order.domain.coupon.CouponIssuanceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CouponIssuancePersistenceAdapter implements CouponIssuanceRepository {

    private final CouponIssuanceJpaRepository jpaRepository;
    private final CouponIssuanceMapper mapper;

    @Override
    public Optional<CouponIssuance> findByCouponCodeAndOwnerId(String couponCode, UUID ownerId) {
        return jpaRepository.findByCouponCodeAndOwnerId(couponCode, ownerId).map(mapper::toDomain);
    }

    @Override
    public Optional<CouponIssuance> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Page<CouponIssuance> findByOwnerId(UUID ownerId, CouponIssuanceStatus status, Pageable pageable) {
        Page<CouponIssuanceJpaEntity> page = status == null
                ? jpaRepository.findByOwnerId(ownerId, pageable)
                : jpaRepository.findByOwnerIdAndStatus(ownerId, status.name(), pageable);
        return page.map(mapper::toDomain);
    }

    @Override
    public long countByCouponCodeAndOwnerId(String couponCode, UUID ownerId) {
        return jpaRepository.countByCouponCodeAndOwnerId(couponCode, ownerId);
    }

    @Override
    public CouponIssuance save(CouponIssuance issuance) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(issuance)));
    }
}
