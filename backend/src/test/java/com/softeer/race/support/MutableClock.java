package com.softeer.race.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

// 시각을 옮길 수 있는 Clock, 기본은 실제 시각이고 걸었을 때만 그 시각에 멈춘다
public final class MutableClock extends Clock {

    // withZone 으로 갈라져 나온 사본도 같은 시각을 봐야 하므로 상태를 공유한다
    private final AtomicReference<Instant> fixedAt;
    private final ZoneId zone;

    public MutableClock(ZoneId zone) {
        this(new AtomicReference<>(), zone);
    }

    private MutableClock(AtomicReference<Instant> fixedAt, ZoneId zone) {
        this.fixedAt = fixedAt;
        this.zone = zone;
    }

    public void fixAt(LocalDateTime now) {
        fixedAt.set(now.atZone(zone).toInstant());
    }

    public void release() {
        fixedAt.set(null);
    }

    @Override
    public Instant instant() {
        Instant fixed = fixedAt.get();
        return fixed != null ? fixed : Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this.zone.equals(zone) ? this : new MutableClock(fixedAt, zone);
    }
}