package com.softeer.race.auth.domain;

/** 세션 토큰 발급 */
public interface SessionTokenGenerator {

    /** 쿠키에 실려 나가고 저장소의 키가 되는 값 */
    String generate();
}
