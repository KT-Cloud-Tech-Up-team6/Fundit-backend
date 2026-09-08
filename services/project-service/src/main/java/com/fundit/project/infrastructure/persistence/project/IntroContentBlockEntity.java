package com.fundit.project.infrastructure.persistence.project;

/**
 * projects.intro_content(JSONB) 한 원소의 영속성 표현. 도메인의 {@code IntroContentBlock}과
 * 필드는 같지만, JSON 저장 포맷(enum이 아닌 문자열 type)과 도메인 타입을 분리하기 위해 따로 둔다.
 */
record IntroContentBlockEntity(String type, String value) {
}
