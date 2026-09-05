package com.fundit.project.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.community.CommunityService;
import com.fundit.project.infrastructure.persistence.community.CommunityAnswerJpaEntity;
import com.fundit.project.infrastructure.persistence.community.CommunityPostJpaEntity;
import com.fundit.project.infrastructure.security.CurrentMember;
import com.fundit.project.infrastructure.security.CurrentMemberArgumentResolver;
import com.fundit.project.presentation.dto.CommunityAnswerRequest;
import com.fundit.project.presentation.dto.CommunityAnswerResponse;
import com.fundit.project.presentation.dto.CommunityAnswerSummary;
import com.fundit.project.presentation.dto.CommunityPostCreateRequest;
import com.fundit.project.presentation.dto.CommunityPostListItemResponse;
import com.fundit.project.presentation.dto.CommunityPostResponse;
import com.fundit.project.presentation.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** PROJECT-017, PROJECT-018, PROJECT-020, PROJECT-024, PROJECT-025 — 커뮤니티 질문/응원, 답변. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommunityController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CommunityService communityService;

    @PostMapping("/projects/{projectId}/community/posts")
    public ResponseEntity<CommunityPostResponse> createPost(
            @CurrentMember UUID memberId, @PathVariable UUID projectId,
            @Valid @RequestBody CommunityPostCreateRequest request) {
        CommunityPostJpaEntity post = communityService.createPost(memberId, projectId, request.postType(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new CommunityPostResponse(post.getId(), post.getPostType(), post.getContent(), post.getCreatedAt()));
    }

    /**
     * 미로그인도 조회 가능(선택적 인증) — @CurrentMember 대신 헤더를 직접 읽는다.
     * answeredOnly는 판매자 전용이며, 본인 소유가 아니면 CommunityService가 조용히 무시한다.
     */
    @GetMapping("/projects/{projectId}/community/posts")
    public PageResponse<CommunityPostListItemResponse> listPosts(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String postType,
            @RequestParam(defaultValue = "false") boolean answeredOnly,
            @RequestHeader(value = CurrentMemberArgumentResolver.ACCOUNT_ID_HEADER, required = false) String accountIdHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        UUID callerId = parseOrNull(accountIdHeader);

        var result = communityService.listPosts(projectId, postType, answeredOnly, callerId, PageRequest.of(page, size))
                .map(v -> new CommunityPostListItemResponse(v.postId(), v.postType(), v.content(),
                        v.answer().map(a -> new CommunityAnswerSummary(a.getContent(), a.getUpdatedAt())).orElse(null),
                        v.createdAt()));
        return PageResponse.from(result);
    }

    @PostMapping("/community/posts/{postId}/answer")
    public CommunityAnswerResponse upsertAnswer(
            @CurrentMember UUID sellerId, @PathVariable Long postId,
            @Valid @RequestBody CommunityAnswerRequest request) {
        CommunityAnswerJpaEntity answer = communityService.upsertAnswer(sellerId, postId, request.content());
        return new CommunityAnswerResponse(postId, new CommunityAnswerSummary(answer.getContent(), answer.getUpdatedAt()));
    }

    private UUID parseOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
