package com.fundit.member.infrastructure.seed;

import com.fundit.member.infrastructure.persistence.member.MemberJpaEntity;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * dev 라이브 목업의 판매자 50명을 기동 시 만든다(이슈 #154).
 *
 * <p><b>dev 전용이다</b>({@code @Profile("dev")}) — local·prod·테스트 컨텍스트에는 빈 자체가 없다.
 *
 * <p>리포지토리로 직접 저장한다. SQL로 넣으면 이름·전화번호가 평문으로 들어가 조회 때 복호화에서
 * 터지는데, JPA를 거치면 {@code EncryptedStringConverter}가 암호화한다. 가입 서비스를 태우지 않아
 * {@code member.signed-up.v1}도 발행되지 않는다(목업이 order 쪽으로 새지 않는다). 로그인할 일이 없어
 * auth 계정도 만들지 않는다.
 *
 * <p>id는 {@code seed/mock-sellers.json}의 고정 UUID다 — project·live 시더가 같은 값을
 * {@code seller_id}로 쓴다. 이미 있으면 건너뛰어 재배포해도 중복되지 않는다.
 */
@Slf4j
@Component
@Profile("dev")
public class MockSellerSeeder implements ApplicationRunner {

    static final String SEED_FILE = "seed/mock-sellers.json";

    private final MemberJpaRepository memberRepository;
    private final ObjectMapper objectMapper;

    public MockSellerSeeder(MemberJpaRepository memberRepository, ObjectMapper objectMapper) {
        this.memberRepository = memberRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int created = seed(read());
            log.info("목업 판매자 시드 완료 — 신규 {}명", created);
        } catch (RuntimeException | IOException e) {
            // 기동은 막지 않는다 — 다음 재시작 때 이미 있는 행은 건너뛰고 나머지만 채운다.
            log.warn("목업 판매자 시드 실패", e);
        }
    }

    int seed(List<MockSeller> sellers) {
        int created = 0;
        for (MockSeller s : sellers) {
            if (memberRepository.existsById(s.sellerId())) {
                continue;
            }
            memberRepository.save(MemberJpaEntity.builder()
                    .id(s.sellerId())
                    .name(s.name())
                    .phoneNumber(s.phoneNumber())
                    .nickname(s.nickname())
                    .build());
            created++;
        }
        return created;
    }

    List<MockSeller> read() throws IOException {
        try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() {
            });
        }
    }

    record MockSeller(UUID sellerId, String nickname, String name, String phoneNumber) {
    }
}
