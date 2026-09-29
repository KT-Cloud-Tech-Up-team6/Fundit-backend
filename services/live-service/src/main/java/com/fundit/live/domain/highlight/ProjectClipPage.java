package com.fundit.live.domain.highlight;

import java.util.List;

/** 프로젝트 공개 클립 한 페이지. 도메인 포트가 Spring Data 타입을 모르게 두려고 따로 만든다. */
public record ProjectClipPage(List<ProjectClip> content, long totalElements) {
}
