package com.fundit.project.infrastructure.sellerprofile;

import com.fundit.project.application.project.SellerProfileClient;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** {@link SellerProfileClient} 클래스 주석 참고 — member-service 연동 전 placeholder. */
@Component
public class NoopSellerProfileClient implements SellerProfileClient {

    @Override
    public Optional<String> getDisplayName(UUID sellerId) {
        return Optional.empty();
    }
}
