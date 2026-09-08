package com.fundit.order.infrastructure.persistence.funding;

import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.FundingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FundingPersistenceAdapter implements FundingRepository {

    private final FundingJpaRepository fundingJpaRepository;
    private final FundingLineItemJpaRepository lineItemJpaRepository;
    private final FundingLineItemOptionJpaRepository optionJpaRepository;
    private final FundingMapper mapper;

    @Override
    public Optional<Funding> findByPublicId(UUID publicId) {
        return fundingJpaRepository.findByPublicId(publicId).map(this::hydrate);
    }

    @Override
    public Optional<Funding> findById(Long id) {
        return fundingJpaRepository.findById(id).map(this::hydrate);
    }

    @Override
    public Funding save(Funding domain) {
        boolean isNew = domain.getId() == null;
        FundingJpaEntity saved = fundingJpaRepository.save(mapper.toEntity(domain));
        // 주문 항목(lineItems)은 생성 이후 절대 바뀌지 않으므로(수정 기능 없음) 최초 생성 시에만 적재한다.
        if (isNew) {
            for (FundingLineItem li : domain.getLineItems()) {
                FundingLineItemJpaEntity liEntity = lineItemJpaRepository.save(mapper.toLineItemEntity(saved.getId(), li));
                for (var option : li.options()) {
                    optionJpaRepository.save(mapper.toOptionEntity(liEntity.getId(), option));
                }
            }
        }
        return hydrate(saved);
    }

    @Override
    public Page<Funding> findByMemberId(UUID memberId, FundingStatus status, Pageable pageable) {
        Page<FundingJpaEntity> page = status == null
                ? fundingJpaRepository.findByMemberId(memberId, pageable)
                : fundingJpaRepository.findByMemberIdAndStatus(memberId, status.name(), pageable);
        return page.map(this::hydrate);
    }

    @Override
    public List<Funding> findPendingExpiredBefore(Instant threshold) {
        return fundingJpaRepository.findByStatusAndPaymentExpiresAtBefore(FundingStatus.PENDING.name(), threshold)
                .stream().map(this::hydrate).toList();
    }

    @Override
    public List<Funding> findActiveByProjectId(Long projectId) {
        List<String> activeStatuses = List.of(FundingStatus.PENDING.name(), FundingStatus.FUNDING_IN_PROGRESS.name());
        return fundingJpaRepository.findByProjectIdAndStatusIn(projectId, activeStatuses)
                .stream().map(this::hydrate).toList();
    }

    private Funding hydrate(FundingJpaEntity entity) {
        List<FundingLineItemJpaEntity> lineItemEntities = lineItemJpaRepository.findByFundingId(entity.getId());
        List<Long> lineItemIds = lineItemEntities.stream().map(FundingLineItemJpaEntity::getId).toList();
        List<FundingLineItemOptionJpaEntity> optionEntities = lineItemIds.isEmpty()
                ? List.of() : optionJpaRepository.findByFundingLineItemIdIn(lineItemIds);
        Map<Long, List<FundingLineItemOptionJpaEntity>> optionsByLineItem = optionEntities.stream()
                .collect(Collectors.groupingBy(FundingLineItemOptionJpaEntity::getFundingLineItemId));
        return mapper.toDomain(entity, lineItemEntities, optionsByLineItem);
    }
}
