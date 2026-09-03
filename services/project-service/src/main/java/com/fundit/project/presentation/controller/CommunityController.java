package com.fundit.project.presentation.controller;

import com.fundit.project.application.community.CommunityService;
import com.fundit.project.domain.community.PostType;
import com.fundit.project.presentation.dto.common.ListResponse;
import com.fundit.project.presentation.dto.common.MessageResponse;
import com.fundit.project.presentation.dto.community.CommunityAnswerRequest;
import com.fundit.project.presentation.dto.community.CommunityPostCreateRequest;
import com.fundit.project.presentation.dto.community.CommunityPostIdResponse;
import com.fundit.project.presentation.dto.community.CommunityPostResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/community")
public class CommunityController {

    private final CommunityService communityService;

    @GetMapping
    public ListResponse<CommunityPostResponse> list(@PathVariable UUID projectId,
                                                    @RequestParam(required = false) PostType postType,
                                                    @RequestParam(required = false) Boolean answered) {
        return ListResponse.of(communityService.list(projectId, postType, answered).stream()
                .map(CommunityPostResponse::from)
                .toList());
    }

    @PostMapping("/questions")
    public ResponseEntity<CommunityPostIdResponse> createPost(
            @PathVariable UUID projectId,
            @Valid @RequestBody CommunityPostCreateRequest request) {

        var post = communityService.createPost(projectId, request.postType(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CommunityPostIdResponse(post.getId()));
    }

    @PostMapping("/{postId}/answers")
    public MessageResponse answer(@PathVariable UUID projectId,
                                  @PathVariable Long postId,
                                  @Valid @RequestBody CommunityAnswerRequest request) {
        communityService.upsertAnswer(projectId, postId, request.content());
        return MessageResponse.ok();
    }
}
