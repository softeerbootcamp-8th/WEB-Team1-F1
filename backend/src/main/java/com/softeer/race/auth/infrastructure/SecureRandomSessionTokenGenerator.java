package com.softeer.race.auth.infrastructure;

import com.softeer.race.auth.domain.SessionTokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class SecureRandomSessionTokenGenerator implements SessionTokenGenerator {

    // 256비트, 추측 가능성을 실질적으로 없애는 수준이라 만료 외에는 방어 장치가 필요하지 않다
    private static final int TOKEN_BYTE_LENGTH = 32;

    // SecureRandom은 스레드 세이프하고 초기화가 비싸므로 인스턴스를 재사용한다
    private final SecureRandom secureRandom = new SecureRandom();

    /** URL-safe Base64 43자, 쿠키 값에 그대로 실을 수 있어 추가 인코딩이 필요 없다 */
    @Override
    public String generate() {
        byte[] token = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }
}
