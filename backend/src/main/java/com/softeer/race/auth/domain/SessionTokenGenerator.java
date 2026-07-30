package com.softeer.race.auth.domain;

/** 세션 토큰의 발급과 저장용 단방향 변환 */
public interface SessionTokenGenerator {

    /** 쿠키에 실려 나가는 원문 토큰 */
    String generate();

    /** DB에 PK로 남길 값, 같은 원문은 항상 같은 값이 되어야 조회가 가능하다 */
    String hash(String rawToken);
}
