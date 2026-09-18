package com.fundit.fulfillment.application.tracker;

import com.fundit.fulfillment.application.funding.FundingSuccessEventListener;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * FULFILLMENT-001 — 펀딩 성립 이벤트 구독으로 트래커를 초기화한다. {@code fulfillment_trackers}를
 * 만드는 유일한 경로이며(API로 직접 생성하지 않음), 같은 project_id로 중복 이벤트를 받아도
 * idempotent해야 한다.
 *
 * <p>신규 이벤트는 {@code projectPublicId}를 쓰고, 구버전 Long Kafka payload는
 * project-service 내부 API로 publicId를 해석한다.
 */
@Service
@RequiredArgsConstructor
public class FulfillmentTrackerInitializationService implements FundingSuccessEventListener {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentTrackerInitializationService.class);

    private final FulfillmentTrackerRepository trackerRepository;
    private final ProjectOwnershipClient projectOwnershipClient;

    @Override
    @Transactional
    public void onFundingSucceeded(FundingSucceededEvent event) {
        UUID projectId = resolveProjectPublicId(event);
        if (trackerRepository.existsByProjectId(projectId)) {
            log.info("이미 트래커가 존재해 이벤트를 무시합니다(idempotent). projectId={}", projectId);
            return;
        }
        try {
            trackerRepository.save(FulfillmentTracker.create(projectId));
        } catch (DataIntegrityViolationException e) {
            // 존재 확인과 저장 사이의 레이스 컨디션으로 중복 이벤트가 동시에 들어온 경우.
            // uq_fulfillment_trackers_project_public 위반을 정상 케이스로 처리한다(idempotent).
            log.info("트래커 동시 생성 충돌을 정상 처리합니다(idempotent). projectId={}", projectId);
        }
    }

    private UUID resolveProjectPublicId(FundingSucceededEvent event) {
        if (event.projectPublicId() != null) {
            return event.projectPublicId();
        }
        return projectOwnershipClient.getPublicId(event.projectId());
    }
}
