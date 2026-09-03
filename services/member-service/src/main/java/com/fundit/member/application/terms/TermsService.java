package com.fundit.member.application.terms;

import com.fundit.member.infrastructure.terms.TermsCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TermsService {

    private final TermsCatalog termsCatalog;

    public List<TermsCatalog.Terms> getTerms() {
        return termsCatalog.findAll();
    }
}
