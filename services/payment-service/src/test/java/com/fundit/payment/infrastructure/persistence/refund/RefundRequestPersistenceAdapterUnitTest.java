package com.fundit.payment.infrastructure.persistence.refund;

import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.infrastructure.security.AesGcmCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundRequestPersistenceAdapterUnitTest {

    @Mock
    private RefundRequestJpaRepository jpaRepository;

    private RefundRequestMapper mapper;
    private RefundRequestPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        mapper = new RefundRequestMapper(new AesGcmCipher(Base64.getEncoder().encodeToString(new byte[32])),
                new ObjectMapper());
        adapter = new RefundRequestPersistenceAdapter(jpaRepository, mapper);
    }

    @Test
    void 저장과_조회가_매퍼를_거친다() {
        RefundRequest request = RefundRequest.requestDefect(1024L, UUID.randomUUID(), "파손", List.of("url"))
                .toBuilder().id(11L).build();
        when(jpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jpaRepository.findById(11L)).thenReturn(Optional.of(mapper.toEntity(request)));
        when(jpaRepository.findById(99L)).thenReturn(Optional.empty());

        RefundRequest saved = adapter.save(request);
        assertThat(saved.getFundingId()).isEqualTo(1024L);
        assertThat(adapter.findById(11L)).isPresent();
        assertThat(adapter.findById(99L)).isEmpty();
    }
}
