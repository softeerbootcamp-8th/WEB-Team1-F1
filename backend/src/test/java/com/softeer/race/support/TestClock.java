package com.softeer.race.support;

import java.time.ZoneId;

// 통합테스트가 함께 보는 시각, 부모 클래스와 시더가 같은 인스턴스를 움직인다
public final class TestClock {

    public static final MutableClock INSTANCE = new MutableClock(ZoneId.of("Asia/Seoul"));

    private TestClock() {
    }
}