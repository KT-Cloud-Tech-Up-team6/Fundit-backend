package com.fundit.member.infrastructure.persistence;

import com.fundit.member.infrastructure.security.AesGcmCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 개인정보 컬럼을 저장할 때 암호화하고 읽을 때 복호화한다(security.md S9).
 *
 * <p>member는 전부 단순 애그리거트라 도메인/Mapper가 없다(persistence-convention.md §2).
 * 암호화하겠다고 도메인·Mapper·Adapter를 만들면 파일만 늘어나므로, JPA 표준 기능인
 * {@link AttributeConverter}로 엔티티 필드에 한 줄씩 붙인다. 애플리케이션 코드는 평문만 본다.
 *
 * <p><b>{@code autoApply}를 켜지 않는다.</b> 켜면 닉네임·주소별칭까지 전부 암호화되어
 * 조회·정렬이 조용히 깨진다. 암호화할 컬럼에만 {@code @Convert}를 붙인다.
 *
 * <p>Hibernate가 직접 인스턴스화하지 않고 Spring 빈을 쓰는 이유: 키를 주입받아야 한다.
 * Spring Boot가 등록하는 {@code SpringBeanContainer}가 이 빈을 찾아준다.
 *
 * <p><b>이 컬럼으로는 검색할 수 없다.</b> {@link AesGcmCipher}는 IV를 매번 새로 만들어 같은
 * 평문도 매번 다른 암호문이 된다. 조회 조건이 필요해지면 블라인드 인덱스 컬럼을 따로 둬야 한다
 * (auth-service {@code BlindIndex} 참고).
 */
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final AesGcmCipher cipher;

    @Override
    public String convertToDatabaseColumn(String plainText) {
        return plainText == null ? null : cipher.encrypt(plainText);
    }

    @Override
    public String convertToEntityAttribute(String cipherText) {
        return cipherText == null ? null : cipher.decrypt(cipherText);
    }
}
