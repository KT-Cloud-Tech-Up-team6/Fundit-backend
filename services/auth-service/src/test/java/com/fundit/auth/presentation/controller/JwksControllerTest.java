package com.fundit.auth.presentation.controller;

import com.fundit.auth.domain.account.Role;
import com.fundit.auth.infrastructure.security.JwtTestKeys;
import com.fundit.auth.infrastructure.security.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스프링 컨텍스트 없이 검증한다 — 컨트롤러는 JwtTokenProvider가 만든 맵을 그대로 돌려주는 위임이다.
 *
 * <p><b>반드시 직렬화 결과까지 확인한다.</b> 처음엔 jjwt의 {@code JwkSet}을 그대로 반환하고
 * 테스트도 Java 객체({@code getKeys()})만 봤는데, 실제로 앱을 띄워 보니 Jackson이
 * {@code {"keys":{}}}로 비워서 내보내고 있었다 — 테스트는 통과하는데 응답은 쓸모없는 상태였다.
 * 그래서 여기서는 Jackson으로 직렬화한 뒤 그 결과를 검증한다.
 *
 * <p>예외 테스트는 두지 않는다: 입력도 검증도 비즈니스 규칙도 없는 정적 공개키 응답이라
 * 예외 케이스 자체가 존재하지 않는다(test-convention.md).
 */
class JwksControllerTest {

    private final JwtTokenProvider jwtTokenProvider =
            JwtTestKeys.provider(JwtTestKeys.PRIVATE_KEY, Duration.ofMinutes(30));
    private final JwksController controller = new JwksController(jwtTokenProvider);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 직렬화하면_keys_배열에_RSA_공개키가_담긴다() {
        // when — 게이트웨이가 실제로 받아보는 형태로 확인한다
        Map<String, Object> parsed = serializeAndParse();

        // then
        assertThat(parsed.get("keys")).isInstanceOf(List.class);
        Map<String, Object> jwk = firstKey(parsed);
        assertThat(jwk.get("kty")).isEqualTo("RSA");
        assertThat((String) jwk.get("kid")).isNotBlank();
        assertThat(jwk).containsKeys("n", "e");
    }

    @Test
    void 개인키_파라미터는_노출하지_않는다() {
        // given — 공개키 JWK에 d/p/q 같은 개인 파라미터가 섞이면 개인키가 그대로 유출된다
        Map<String, Object> jwk = firstKey(serializeAndParse());

        // then
        assertThat(jwk).doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
    }

    @Test
    void 공개된_키로_실제_발급_토큰의_서명을_검증할_수_있고_kid도_일치한다() {
        // given — 게이트웨이가 하는 일을 그대로 재현한다: JWKS의 n/e로 공개키를 만들어 검증
        Map<String, Object> jwk = firstKey(serializeAndParse());
        String token = jwtTokenProvider.issueAccessToken(UUID.randomUUID(), Role.MEMBER);

        // when
        String tokenKid = Jwts.parser()
                .verifyWith(publicKeyFrom(jwk))
                .build()
                .parseSignedClaims(token)
                .getHeader()
                .getKeyId();

        // then
        assertThat(tokenKid).isEqualTo(jwk.get("kid"));
    }

    private Map<String, Object> serializeAndParse() {
        String json = objectMapper.writeValueAsString(controller.jwks());
        return objectMapper.readValue(json, Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstKey(Map<String, Object> jwkSet) {
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwkSet.get("keys");
        assertThat(keys).as("keys 배열이 비어 있으면 게이트웨이가 검증할 키가 없다").hasSize(1);
        return keys.get(0);
    }

    private PublicKey publicKeyFrom(Map<String, Object> jwk) {
        try {
            BigInteger modulus = base64UrlToBigInteger((String) jwk.get("n"));
            BigInteger exponent = base64UrlToBigInteger((String) jwk.get("e"));
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (Exception e) {
            throw new IllegalStateException("JWKS의 n/e로 공개키를 만들지 못했다", e);
        }
    }

    private BigInteger base64UrlToBigInteger(String value) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(value));
    }
}
