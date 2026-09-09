package com.fundit.auth.infrastructure.security;

import com.fundit.auth.domain.account.Role;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.PublicJwk;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.UUID;

/**
 * RS256으로 서명하고 검증한다. 개인키는 이 서비스만 갖고, 게이트웨이는 JWKS 엔드포인트로
 * 공개키만 가져가 검증한다 — 대칭키를 두 서비스에 뿌리지 않기 위함.
 *
 * <p>공개키는 설정으로 따로 받지 않고 개인키에서 유도한다. RSA PKCS#8 개인키는 CRT 파라미터를
 * 포함하므로 modulus/publicExponent만 있으면 공개키를 그대로 만들 수 있다 — 덕분에 배포 환경이
 * 관리할 시크릿이 {@code jwt.private-key} 하나로 유지된다.
 */
@Component
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";
    private static final String TYPE_CLAIM = "typ";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    private final JwtProperties properties;
    private final PrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;
    private final Map<String, Object> publicJwkSet;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;

        RSAPrivateCrtKey rsaPrivateKey = loadPrivateKey(properties.getPrivateKey());
        this.privateKey = rsaPrivateKey;
        this.publicKey = derivePublicKey(rsaPrivateKey);

        // kid는 설정값이 아니라 공개키의 지문(RFC 7638)에서 뽑는다 — 키가 바뀌면 kid도 자동으로
        // 따라가므로 로테이션 시 코드/설정을 고칠 게 없다.
        PublicJwk<RSAPublicKey> jwk = Jwks.builder().key(this.publicKey).idFromThumbprint().build();
        this.keyId = jwk.getId();
        this.publicJwkSet = Map.of("keys", List.of(new LinkedHashMap<String, Object>(jwk)));
    }

    /**
     * JWKS 엔드포인트가 그대로 응답할 공개키 집합.
     *
     * <p>jjwt의 {@code JwkSet}/{@code Jwk}는 {@code Map}을 구현하지만 Jackson에 그대로 넘기면
     * {@code {"keys":{}}}로 비어서 나간다(실기동으로 확인) — 내부 구현이 Jackson이 기대하는
     * 방식으로 엔트리를 노출하지 않는다. 그래서 평범한 Map/List로 복사해서 돌려준다.
     * {@code PublicJwk}에는 공개 파라미터만 들어 있으므로 이 복사로 개인키가 새지 않는다.
     */
    public Map<String, Object> publicJwkSet() {
        return publicJwkSet;
    }

    public String issueAccessToken(UUID accountId, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(keyId).and()
                .subject(accountId.toString())
                .claim(ROLE_CLAIM, role.name())
                .claim(TYPE_CLAIM, ACCESS_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTokenTtl())))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String issueRefreshToken(UUID tokenId, UUID accountId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(keyId).and()
                .id(tokenId.toString())
                .subject(accountId.toString())
                .claim(TYPE_CLAIM, REFRESH_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getRefreshTokenTtl())))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public AccessTokenClaims parseAccessToken(String token) {
        var claims = parse(token);
        requireType(claims, ACCESS_TYPE);
        return new AccessTokenClaims(
                UUID.fromString(claims.getSubject()),
                Role.valueOf(claims.get(ROLE_CLAIM, String.class)));
    }

    /**
     * 재사용 탐지(AUTH-003)를 위해 서명은 유효하지만 DB에 없는 토큰의 경우에도
     * account_id 클레임을 읽어야 해서, 서명 유효성과 무관하게 클레임 파싱 자체는 여기서 끝낸다.
     * 서명 자체가 무효(TOKEN_INVALID)/만료(TOKEN_EXPIRED)면 예외를 던져 호출부가 더 진행하지 않게 한다.
     */
    public RefreshTokenClaims parseRefreshToken(String token) {
        var claims = parse(token);
        requireType(claims, REFRESH_TYPE);
        return new RefreshTokenClaims(
                UUID.fromString(claims.getId()),
                UUID.fromString(claims.getSubject()));
    }

    /** access/refresh 토큰이 서로의 파서에 제출돼도 통과되지 않도록 타입 클레임을 확인한다. */
    private void requireType(io.jsonwebtoken.Claims claims, String expectedType) {
        if (!expectedType.equals(claims.get(TYPE_CLAIM, String.class))) {
            throw new BusinessException(CommonErrorCode.TOKEN_INVALID);
        }
    }

    private io.jsonwebtoken.Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(publicKey).build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(CommonErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.TOKEN_INVALID);
        }
    }

    private static RSAPrivateCrtKey loadPrivateKey(String base64Pkcs8) {
        byte[] der;
        try {
            der = Base64.getDecoder().decode(base64Pkcs8);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "jwt.private-key가 base64가 아닙니다. PKCS#8 DER을 base64로 인코딩한 한 줄 문자열이어야 합니다.", e);
        }

        PrivateKey key;
        try {
            key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("jwt.private-key를 RSA 개인키로 읽을 수 없습니다.", e);
        }

        // 공개키를 유도하려면 공개지수가 필요하고, 그건 CRT 형식에만 들어 있다.
        // 그냥 캐스팅하면 ClassCastException만 나서 원인을 알기 어려우므로 여기서 끊는다.
        if (!(key instanceof RSAPrivateCrtKey crtKey)) {
            throw new IllegalStateException(
                    "jwt.private-key는 CRT 파라미터를 포함한 RSA 개인키여야 합니다(공개키 유도에 필요). "
                            + "openssl genpkey -algorithm RSA 로 생성한 PKCS#8 키를 사용하세요.");
        }
        return crtKey;
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateCrtKey privateKey) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("개인키에서 공개키를 유도하지 못했습니다.", e);
        }
    }

    public record AccessTokenClaims(UUID accountId, Role role) {
    }

    public record RefreshTokenClaims(UUID tokenId, UUID accountId) {
    }
}
