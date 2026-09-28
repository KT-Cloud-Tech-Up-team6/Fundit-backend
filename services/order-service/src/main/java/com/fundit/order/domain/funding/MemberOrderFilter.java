package com.fundit.order.domain.funding;

import java.time.Instant;
import java.util.List;

/**
 * ORDER-004 내 펀딩 목록 필터. 모두 선택값이다 — 비어 있으면 그 조건을 걸지 않는다.
 *
 * @param statuses 주문 상태(여러 개면 OR). 빈 목록이면 전체
 * @param q        프로젝트명 부분 일치(대소문자 무시). 공백뿐이면 null로 정규화
 * @param from     참여일시 하한(포함)
 * @param to       참여일시 상한(미포함)
 */
public record MemberOrderFilter(List<FundingStatus> statuses, String q, Instant from, Instant to) {

    public MemberOrderFilter {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        q = q == null || q.isBlank() ? null : q.trim();
    }

    public static MemberOrderFilter ofStatus(FundingStatus status) {
        return new MemberOrderFilter(status == null ? List.of() : List.of(status), null, null, null);
    }
}
