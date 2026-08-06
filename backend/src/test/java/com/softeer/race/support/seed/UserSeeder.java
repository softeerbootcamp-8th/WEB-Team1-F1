package com.softeer.race.support.seed;

import com.softeer.race.support.TestClock;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 실명과 역할만 정하면 나머지는 알아서 채워지는 회원
 */
// 유일 제약이 걸린 값은 일련번호로 나눈다, 테이블을 비워도 되돌아가지 않아 앞 테스트가 쓴 값과 겹치지 않는다
@RequiredArgsConstructor
public class UserSeeder {

    private static final AtomicLong SERIAL = new AtomicLong();
    private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 7, 1, 10, 0);

    private final UserRepository userRepository;

    public User user(String realName, Role role) {
        long serial = SERIAL.incrementAndGet();

        return TestClock.INSTANCE.at(JOINED_AT, () -> userRepository.save(User.create(
                "user" + serial,
                "user" + serial + "@race.dev",
                "pw",
                realName,
                "010%08d".formatted(serial),
                role)));
    }
}