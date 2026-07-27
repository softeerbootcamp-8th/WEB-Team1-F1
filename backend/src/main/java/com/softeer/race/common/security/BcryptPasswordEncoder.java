package com.softeer.race.common.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

@Component
public class BcryptPasswordEncoder implements PasswordEncoder {

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
