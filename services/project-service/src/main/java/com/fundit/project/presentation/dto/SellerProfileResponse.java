package com.fundit.project.presentation.dto;

import java.util.List;
import java.util.UUID;

public record SellerProfileResponse(UUID sellerId, String businessType, List<PastProjectResponse> pastProjects) {
}
