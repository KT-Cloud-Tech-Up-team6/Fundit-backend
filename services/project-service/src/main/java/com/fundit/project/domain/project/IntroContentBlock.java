package com.fundit.project.domain.project;

/** 소개 콘텐츠 한 블록(텍스트/이미지/영상 링크 중 하나). JSONB(intro_content)로 저장된다. */
public record IntroContentBlock(IntroContentType type, String value) {
}
