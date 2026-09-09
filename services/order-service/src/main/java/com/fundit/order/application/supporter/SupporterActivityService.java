package com.fundit.order.application.supporter;

import com.fundit.order.application.member.MemberDisclosureClient;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.query.SupporterActivityProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * ORDER-001 — 서포터 활동 목록 조회. 순수 조회 전용(도메인 로직 없음)이라
 * persistence-convention.md §3에 따라 application이 프로젝션 JpaRepository를 직접 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupporterActivityService {

    private final FundingJpaRepository fundingJpaRepository;
    private final MemberDisclosureClient memberDisclosureClient;

    public Page<SupporterActivity> list(Long projectId, Pageable pageable) {
        return fundingJpaRepository.findSupporterActivity(projectId, pageable).map(this::toView);
    }

    private SupporterActivity toView(SupporterActivityProjection projection) {
        boolean publicConsent = memberDisclosureClient.isPublicConsent(projection.getMemberId());
        // 실제 닉네임은 member-service 소유라 아직 조회하지 않는다[가정] — 공개 동의 시에도
        // 일반화된 표시명만 노출하고, 비공개면 금액과 함께 완전히 마스킹한다(security.md S9).
        String displayName = publicConsent ? "구매자" : "익명";
        Long amount = publicConsent ? projection.getAmount() : null;
        return new SupporterActivity(displayName, amount, projection.getCreatedAt());
    }

    public record SupporterActivity(String displayName, Long amount, Instant activityAt) {
    }
}
