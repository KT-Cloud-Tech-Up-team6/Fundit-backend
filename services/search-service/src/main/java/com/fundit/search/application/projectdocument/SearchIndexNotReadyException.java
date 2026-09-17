package com.fundit.search.application.projectdocument;

/**
 * SEARCH-011 색인 행이 아직 없어 후행 이벤트(상태·찜)를 적용할 수 없을 때 던진다.
 * Kafka 컨슈머가 재시도·DLT로 보류하도록 처리 실패로 남긴다.
 */
public class SearchIndexNotReadyException extends RuntimeException {

    public SearchIndexNotReadyException(Long projectId) {
        super("색인에 없는 projectId — 재시도 예정: " + projectId);
    }
}
