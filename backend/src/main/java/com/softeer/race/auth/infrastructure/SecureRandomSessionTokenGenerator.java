package com.softeer.race.auth.infrastructure;

import com.softeer.race.auth.domain.SessionTokenGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class SecureRandomSessionTokenGenerator implements SessionTokenGenerator {

    // 256비트, 추측 가능성을 실질적으로 없애는 수준이라 만료 외에는 방어 장치가 필요하지 않다
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    // SecureRandom은 스레드 세이프하고 초기화가 비싸므로 인스턴스를 재사용한다
    private final SecureRandom secureRandom = new SecureRandom();

    /** URL-safe Base64 43자, 쿠키 값에 그대로 실을 수 있어 추가 인코딩이 필요 없다 */
    @Override
    public String generate() {
        byte[] token = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    /**
     * 비밀번호와 달리 bcrypt를 쓰지 않는다. 입력이 이미 256비트 난수라 사전 공격 대상이 아니어서
     * work factor가 무의미하고, 매 요청 100ms를 쓰는 것은 인증 경로에서 치명적이다.
     */
    @Override
    public String hash(String rawToken) {
        // MessageDigest는 스레드 세이프하지 않아 호출마다 새로 얻는다
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(HASH_ALGORITHM + "을 사용할 수 없습니다.", exception);
        }
    }
}
