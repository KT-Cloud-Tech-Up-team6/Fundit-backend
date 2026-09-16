package com.fundit.member.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.member.application.follow.FollowService;
import com.fundit.member.presentation.dto.FollowListItemResponse;
import com.fundit.member.presentation.dto.FollowResponse;
import com.fundit.member.presentation.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 메이커 팔로우(MEMBER-007). 마이페이지의 "찜한 판매자 목록"이 이 목록이다(MEMBER-006).
 *
 * <p>찜 목록({@code GET /api/v1/wishes})과 합치지 않는 이유: 응답 아이템 모양이 완전히 다르고,
 * 한 엔드포인트에 섞으면 페이지네이션이 하나로 묶여 탭 전환마다 커서가 꼬인다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FollowController {

    private static final int MAX_PAGE_SIZE = 100;

    private final FollowService followService;

    @PutMapping("/follows/{sellerId}")
    public FollowResponse follow(@LoginUser CurrentUser user, @PathVariable UUID sellerId) {
        followService.follow(user.id(), sellerId);
        return new FollowResponse(sellerId, true);
    }

    @DeleteMapping("/follows/{sellerId}")
    public ResponseEntity<Void> unfollow(@LoginUser CurrentUser user, @PathVariable UUID sellerId) {
        followService.unfollow(user.id(), sellerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/follows")
    public PageResponse<FollowListItemResponse> getFollows(
            @LoginUser CurrentUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        var result = followService.getFollows(user.id(), PageRequest.of(page, size))
                .map(f -> new FollowListItemResponse(f.sellerId(), f.sellerName(), f.sellerNickname(), f.createdAt()));
        return PageResponse.from(result);
    }
}
