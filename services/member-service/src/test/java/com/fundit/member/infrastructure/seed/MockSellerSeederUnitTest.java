package com.fundit.member.infrastructure.seed;

import com.fundit.member.infrastructure.persistence.member.MemberJpaEntity;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MockSellerSeederUnitTest {

    @Mock
    private MemberJpaRepository memberRepository;

    private MockSellerSeeder seeder() {
        return new MockSellerSeeder(memberRepository, JsonMapper.builder().build());
    }

    @Test
    void 시드_파일의_판매자_50명을_고정_UUID로_읽는다() throws Exception {
        // when
        List<MockSellerSeeder.MockSeller> sellers = seeder().read();

        // then — project·live 시더가 같은 UUID를 seller_id로 쓴다. mock_key로 결정적 생성한 값이다.
        assertThat(sellers).hasSize(50);
        assertThat(sellers.getFirst().sellerId()).isEqualTo(
                UUID.nameUUIDFromBytes("fundit-mock-seller:FD-001".getBytes(StandardCharsets.UTF_8)));
        assertThat(sellers).allSatisfy(s -> assertThat(s.nickname()).isNotBlank());
    }

    @Test
    void 없는_판매자만_새로_만든다() {
        // given
        UUID existing = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        given(memberRepository.existsById(existing)).willReturn(true);
        given(memberRepository.existsById(fresh)).willReturn(false);

        // when
        int created = seeder().seed(List.of(
                new MockSellerSeeder.MockSeller(existing, "이미있음", "목업판매자001", "01099990001"),
                new MockSellerSeeder.MockSeller(fresh, "쓱쓱생활연구소", "목업판매자002", "01099990002")));

        // then — 재배포해도 중복 생성되지 않는다
        assertThat(created).isEqualTo(1);
        ArgumentCaptor<MemberJpaEntity> captor = ArgumentCaptor.forClass(MemberJpaEntity.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(fresh);
        assertThat(captor.getValue().getNickname()).isEqualTo("쓱쓱생활연구소");
    }

    @Test
    void 모두_있으면_저장하지_않는다() {
        // given
        UUID existing = UUID.randomUUID();
        given(memberRepository.existsById(existing)).willReturn(true);

        // when
        int created = seeder().seed(List.of(
                new MockSellerSeeder.MockSeller(existing, "이미있음", "목업판매자001", "01099990001")));

        // then
        assertThat(created).isZero();
        verify(memberRepository, never()).save(any());
    }
}
