package com.fundit.auth.domain.account;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * LIVE 채팅 화면이 쓰는 라벨과 같은 닉네임을 가려낸다(#206, FE BE-33).
 * 채팅은 다른 시청자를 닉네임으로 보여 주므로, "판매자"로 가입하면 판매자 답변과 똑같이 보여 사칭할 수 있다.
 *
 * <p>FE는 앞뒤 공백만 걸러 대체 표시하므로 서버는 글자 사이 공백·폭 없는 문자·전각·대소문자 변형까지 같은 값으로 본다.
 * 부분 일치("판매자123")는 막지 않는다 — 정상 닉네임이 과하게 걸린다.
 */
public final class ReservedNickname {

    /** 정규화 후 값. 채팅 화면 라벨이 늘면 같이 추가한다. */
    private static final Set<String> RESERVED = Set.of("판매자", "나", "ai매니저", "시청자");

    private ReservedNickname() {
    }

    public static boolean isReserved(String nickname) {
        if (nickname == null) {
            return false;
        }
        // NFKC를 먼저 한다 — NBSP(U+00A0)는 isWhitespace가 false지만 NFKC 후 일반 공백이 돼 같이 빠지고, 전각 ＡＩ도 AI가 된다
        String normalized = Normalizer.normalize(nickname, Normalizer.Form.NFKC).codePoints()
                .filter(cp -> !Character.isWhitespace(cp) && !isZeroWidth(cp))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .toLowerCase(Locale.ROOT);
        return RESERVED.contains(normalized);
    }

    private static boolean isZeroWidth(int cp) {
        return (cp >= 0x200B && cp <= 0x200D) || cp == 0x2060 || cp == 0xFEFF;
    }
}
