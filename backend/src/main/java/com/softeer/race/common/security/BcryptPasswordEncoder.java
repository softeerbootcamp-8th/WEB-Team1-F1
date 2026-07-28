package com.softeer.race.common.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

@Component
public class BcryptPasswordEncoder implements PasswordEncoder {

    // OWASP가 권장하는 최소 work factor. 값을 1 올릴 때마다 해싱 시간이 두 배가 되므로
    // 무차별 대입 방어와 가입 응답 속도의 절충점으로 하한을 택했다
    private static final int COST = 10;

    @Override
    public String encode(String rawPassword) {
        return BCrypt.withDefaults().hashToString(COST, rawPassword.toCharArray());
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return BCrypt.verifyer()
                .verify(rawPassword.toCharArray(), encodedPassword.toCharArray())
                .verified;
    }
}
