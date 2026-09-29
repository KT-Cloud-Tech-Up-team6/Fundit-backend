package com.fundit.member.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.member.application.wish.WishService;
import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.member.presentation.dto.PageResponse;
import com.fundit.member.presentation.dto.WishListItemResponse;
import com.fundit.member.presentation.dto.WishResponse;
import com.fundit.member.presentation.dto.WishStatusResponse;
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


@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WishController {

    private static final int MAX_PAGE_SIZE = 100;

    private final WishService wishService;

    @PutMapping("/wishes/{projectId}")
    public WishResponse wish(@LoginUser CurrentUser user, @PathVariable Long projectId) {
        wishService.wish(user.id(), projectId);
        return new WishResponse(projectId, true);
    }

    @DeleteMapping("/wishes/{projectId}")
    public ResponseEntity<Void> unwish(@LoginUser CurrentUser user, @PathVariable Long projectId) {
        wishService.unwish(user.id(), projectId);
        return ResponseEntity.noContent().build();
    }

    /** 프로젝트 상세(UUID 기준) — 숫자 id API와 같은 찜 데이터를 쓴다. 스냅샷이 없는 프로젝트면 404. */
    @GetMapping("/wishes/projects/{projectPublicId}")
    public WishStatusResponse getWishStatus(@LoginUser CurrentUser user, @PathVariable UUID projectPublicId) {
        return new WishStatusResponse(projectPublicId, wishService.isWished(user.id(), projectPublicId));
    }

    @PutMapping("/wishes/projects/{projectPublicId}")
    public WishStatusResponse wishByPublicId(@LoginUser CurrentUser user, @PathVariable UUID projectPublicId) {
        wishService.wish(user.id(), projectPublicId);
        return new WishStatusResponse(projectPublicId, true);
    }

    @DeleteMapping("/wishes/projects/{projectPublicId}")
    public ResponseEntity<Void> unwishByPublicId(@LoginUser CurrentUser user, @PathVariable UUID projectPublicId) {
        wishService.unwish(user.id(), projectPublicId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/wishes")
    public PageResponse<WishListItemResponse> getWishes(
            @LoginUser CurrentUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        var result = wishService.getWishes(user.id(), PageRequest.of(page, size))
                .map(w -> new WishListItemResponse(w.projectId(), w.projectPublicId(), w.projectTitle(),
                        w.projectThumbnailUrl(), w.createdAt()));
        return PageResponse.from(result);
    }
}
