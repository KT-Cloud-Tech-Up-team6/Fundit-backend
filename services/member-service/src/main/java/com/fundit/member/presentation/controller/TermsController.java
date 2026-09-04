package com.fundit.member.presentation.controller;

import com.fundit.member.application.terms.TermsService;
import com.fundit.member.presentation.dto.TermsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
public class TermsController {

    private final TermsService termsService;

    @GetMapping
    public List<TermsResponse> getTerms() {
        return termsService.getTerms().stream()
                .map(t -> new TermsResponse(t.code(), t.title(), t.content(), t.required(), t.version()))
                .toList();
    }
}
