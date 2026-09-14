package com.fundit.project.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.project.application.media.MediaStorageClient;
import com.fundit.project.application.media.MediaUploadService;
import com.fundit.project.presentation.dto.MediaUploadUrlRequest;
import com.fundit.project.presentation.dto.MediaUploadUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 이미지/영상 업로드 주소 발급(S3 Presigned URL) — 스토리(PROJECT-006)/리워드(PROJECT-007) 공용. */
@Tag(name = "media")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaUploadService mediaUploadService;

    @Operation(summary = "업로드 주소 발급",
            description = "S3 Presigned PUT URL(uploadUrl)과 최종 접근 주소(fileUrl)를 발급한다. "
                    + "파일 바이트는 서버를 거치지 않고 클라이언트가 uploadUrl로 S3에 직접 PUT한다.")
    @PostMapping("/upload-url")
    public MediaUploadUrlResponse issueUploadUrl(
            @LoginUser CurrentUser user, @PathVariable UUID projectId,
            @Valid @RequestBody MediaUploadUrlRequest request) {
        MediaStorageClient.PresignedUpload presigned = mediaUploadService.issueUploadUrl(
                user.id(), projectId, request.fileName(), request.contentType(), request.fileSize());
        return new MediaUploadUrlResponse(presigned.uploadUrl(), presigned.fileUrl());
    }
}
