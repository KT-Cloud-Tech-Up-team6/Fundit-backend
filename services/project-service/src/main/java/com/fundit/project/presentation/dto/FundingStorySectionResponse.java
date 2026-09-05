package com.fundit.project.presentation.dto;

import java.util.List;

public record FundingStorySectionResponse(String type, String title, String body, List<String> images) {
}
