package com.softeer.race.common.security;

// Spring Security를 도입하지 않으므로 해싱 방식만 자체 인터페이스로 추상화한다
// 이름이 같은 org.springframework.security 타입과 혼동하지 않도록 주입 시 import 경로를 확인할 것
public interface PasswordEncoder {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
