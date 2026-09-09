package com.fundit.payment.infrastructure.persistence.settlement;

import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchRepository;
import com.fundit.payment.domain.settlement.SettlementBatchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SettlementBatchPersistenceAdapter implements SettlementBatchRepository {

    private final SettlementBatchJpaRepository batchJpaRepository;
    private final SettlementBatchItemJpaRepository itemJpaRepository;
    private final SettlementBatchMapper mapper;

    @Override
    public SettlementBatch save(SettlementBatch domain) {
        boolean isNew = domain.getId() == null;
        SettlementBatchJpaEntity saved = batchJpaRepository.save(mapper.toEntity(domain));
        // 배치 항목(items)은 생성 이후 갱신되지 않으므로(수정 기능 없음) 최초 생성 시에만 적재한다.
        if (isNew) {
            domain.getItems().forEach(item -> itemJpaRepository.save(mapper.toItemEntity(saved.getId(), item)));
        }
        return hydrate(saved);
    }

    @Override
    public Optional<SettlementBatch> findById(Long id) {
        return batchJpaRepository.findById(id).map(this::hydrate);
    }

    @Override
    public List<SettlementBatch> findPayable() {
        return batchJpaRepository.findByStatus(SettlementBatchStatus.PENDING.name())
                .stream().map(this::hydrate).toList();
    }

    private SettlementBatch hydrate(SettlementBatchJpaEntity entity) {
        return mapper.toDomain(entity, itemJpaRepository.findByBatchId(entity.getId()));
    }
}
