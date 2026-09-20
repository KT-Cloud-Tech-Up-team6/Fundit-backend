package com.fundit.auth.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 암호화된 컬럼을 조회하기 위한 블라인드 인덱스(security.md S9).
 *
 * <p>{@link AesGcmCipher}는 IV를 매번 새로 만들어 <b>같은 평문도 매번 다른 암호문</b>이 된다.
 * 덕분에 안전하지만 {@code WHERE email = ?}가 성립하지 않는다. 그래서 조회용으로 같은 평문이
 * 항상 같은 값이 되는 HMAC 해시를 따로 저장하고, 그 컬럼으로 찾는다.
 *
 * <p><b>결정적 암호화를 쓰지 않는 이유</b>: 같은 값이 같은 암호문이 되어 빈도 분석이 가능해진다.
 * 해시는 복호화가 불가능하므로 본문 보관과 조회 색인을 분리하는 편이 안전하다.
 *
 * <p>{@code label}로 용도를 분리한다 — 같은 전화번호라도 다른 용도면 다른 해시가 되어,
 * 한 컬럼의 해시를 다른 컬럼 조회에 갖다 쓸 수 없다.
 *
 * <p><b>키는 {@code auth.encryption.key}에서 파생한다.</b> {@link AesGcmCipher}(AES-GCM)와
 * 원본 키를 그대로 공유하면 알고리즘이 다르다는 것만 믿는 셈이라, 생성자에서 한 번
 * {@code HMAC(masterKey, "blind-index-v1")}을 계산해 그 결과를 실제 HMAC 키로 쓴다.
 * 새 환경변수 없이 용도별 키 분리를 얻는다.
 */
@Component
public class BlindIndex {

    private static final String ALGORITHM = "HmacSHA256";

    /** 이메일은 대소문자를 구분하지 않는다 — 가입 때와 조회 때 표기가 갈리면 못 찾는다. */
    public static final String LABEL_EMAIL = "email";
    /** 표기가 갈리지 않게 숫자만 남긴다(`010-1234-5678` == `01012345678`). */
    public static final String LABEL_PHONE = "phone";
    public static final String LABEL_NAME = "name";

    private final SecretKeySpec secretKey;

    private static final byte[] DERIVATION_INFO = "blind-index-v1".getBytes(StandardCharsets.UTF_8);

    public BlindIndex(@Value("${auth.encryption.key}") String base64Key) {
        byte[] masterKey = Base64.getDecoder().decode(base64Key);
        if (masterKey.length != 32) {
            throw new IllegalStateException(
                    "auth.encryption.key는 AES-256용 32바이트 키여야 합니다(현재 %d바이트).".formatted(masterKey.length));
        }
        this.secretKey = new SecretKeySpec(derive(masterKey), ALGORITHM);
    }

    private static byte[] derive(byte[] masterKey) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(masterKey, ALGORITHM));
            return mac.doFinal(DERIVATION_INFO);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("블라인드 인덱스 키 파생에 실패했습니다.", e);
        }
    }

    /** 정규화 후 HMAC-SHA256을 계산해 hex로 돌려준다. 값이 null이면 null이다. */
    public String of(String label, String value) {
        if (value == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);
            String normalized = normalize(label, value);
            return HexFormat.of().formatHex(
                    mac.doFinal((label + ":" + normalized).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("블라인드 인덱스 생성에 실패했습니다.", e);
        }
    }

    private String normalize(String label, String value) {
        return switch (label) {
            case LABEL_EMAIL -> value.trim().toLowerCase(Locale.ROOT);
            case LABEL_PHONE -> value.replaceAll("\\D", "");
            default -> value.trim();
        };
    }
}
