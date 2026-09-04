package com.fundit.member.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.member.application.wish.WishService;
import com.fundit.member.infrastructure.security.CurrentMember;
import com.fundit.member.presentation.dto.PageResponse;
import com.fundit.member.presentation.dto.WishListItemResponse;
import com.fundit.member.presentation.dto.WishResponse;
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
    public WishResponse wish(@CurrentMember UUID accountId, @PathVariable Long projectId) {
        wishService.wish(accountId, projectId);
        return new WishResponse(projectId, true);
    }

    @DeleteMapping("/wishes/{projectId}")
    public ResponseEntity<Void> unwish(@CurrentMember UUID accountId, @PathVariable Long projectId) {
        wishService.unwish(accountId, projectId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/wishes")
    public PageResponse<WishListItemResponse> getWishes(
            @CurrentMember UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        var result = wishService.getWishes(accountId, PageRequest.of(page, size))
                .map(w -> new WishListItemResponse(w.projectId(), w.projectTitle(), w.projectThumbnailUrl(), w.createdAt()));
        return PageResponse.from(result);
    }
}
