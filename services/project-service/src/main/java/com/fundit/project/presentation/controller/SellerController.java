package com.fundit.project.presentation.controller;

import com.fundit.project.application.project.SellerService;
import com.fundit.project.presentation.dto.PastProjectResponse;
import com.fundit.project.presentation.dto.SellerProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** PROJECT-021 — 판매자 정보/이력 조회. */
@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    @GetMapping("/{sellerId}")
    public SellerProfileResponse getProfile(@PathVariable UUID sellerId) {
        var profile = sellerService.getProfile(sellerId);
        var pastProjects = profile.pastProjects().stream()
                .map(p -> new PastProjectResponse(p.projectId(), p.title(), p.status()))
                .toList();
        return new SellerProfileResponse(profile.sellerId(), profile.businessType(), pastProjects);
    }
}
