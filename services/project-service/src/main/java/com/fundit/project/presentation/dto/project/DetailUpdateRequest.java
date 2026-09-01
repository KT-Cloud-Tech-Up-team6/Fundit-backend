package com.fundit.project.presentation.dto.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record DetailUpdateRequest(String title,
                                  String thumbnailImageUrl,
                                  Map<String, Object> introContent,
                                  @NotNull @JsonProperty("isDraft") Boolean draft) {
}
