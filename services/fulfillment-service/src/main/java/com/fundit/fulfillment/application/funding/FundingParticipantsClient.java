package com.fundit.fulfillment.application.funding;

import java.util.List;
import java.util.UUID;

/**
 * order-service 내부 API({@code GET /internal/projects/{projectId}/funding-participants})
 * 아웃바운드 포트. 일정 변경 알림(FULFILLMENT-005)이 프로젝트 참여자 전원에게 팬아웃할 때 쓴다
 * — "참여자 목록을 가진 서비스(order)가 수신자 1명당 1건 발행"이라는 규약에 따라, 목록 자체는
 * order-service에서 동기로 받아오고 팬아웃(행 반복 적재)은 이 서비스가 한다.
 */
public interface FundingParticipantsClient {

    /**
     * @throws com.fundit.common.error.DependencyFailureException 호출 실패(타임아웃·5xx 포함) 시
     */
    List<UUID> listParticipantMemberIds(Long projectId);
}
