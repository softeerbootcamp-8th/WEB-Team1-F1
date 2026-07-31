package com.softeer.race.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 주기 작업을 켠다, 지금 쓰는 곳은 끊긴 구독을 걷어내는 경매방 채널 하나다
// 테스트에서는 꺼 둔다, 배경 스레드가 도는 동안 쿼리 수 같은 계약을 재면 값이 흔들린다
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "race.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}