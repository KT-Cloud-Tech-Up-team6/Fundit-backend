package com.fundit.auth.infrastructure.security;

import com.fundit.auth.domain.account.Role;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";
    private static final String TYPE_CLAIM = "typ";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    private final JwtProperties properties;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(UUID accountId, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(accountId.toString())
                .claim(ROLE_CLAIM, role.name())
                .claim(TYPE_CLAIM, ACCESS_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTokenTtl())))
                .signWith(key())
                .compact();
    }

    public String issueRefreshToken(UUID tokenId, UUID accountId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(tokenId.toString())
                .subject(accountId.toString())
                .claim(TYPE_CLAIM, REFRESH_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getRefreshTokenTtl())))
                .signWith(key())
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
            return Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(CommonErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.TOKEN_INVALID);
        }
    }

    public record AccessTokenClaims(UUID accountId, Role role) {
    }

    public record RefreshTokenClaims(UUID tokenId, UUID accountId) {
    }
}
