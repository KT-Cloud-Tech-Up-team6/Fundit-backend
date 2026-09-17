package com.fundit.search.application.search;

import java.util.UUID;

/** 검색 응답 경로에서 분리된 검색 로그 적재 이벤트. 저장 실패가 검색 API에 전파되지 않는다. */
public record SearchQueryLoggedEvent(UUID memberId, String keyword, int resultCount) {
}
