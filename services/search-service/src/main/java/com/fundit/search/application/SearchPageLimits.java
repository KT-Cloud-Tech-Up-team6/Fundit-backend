package com.fundit.search.application;

/**
 * 홈피드·카테고리·검색 목록에 공통으로 쓰는 페이지 크기 한도.
 * 컨트롤러는 잘못된 page/size를 거절하고, 양수 size가 이 상한을 넘으면 여기 값으로 제한한다.
 */
public final class SearchPageLimits {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private SearchPageLimits() {
    }
}
