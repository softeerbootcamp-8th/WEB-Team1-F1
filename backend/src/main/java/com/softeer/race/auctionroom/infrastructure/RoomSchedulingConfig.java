package com.softeer.race.auctionroom.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// 경매방 주기 작업이 어느 스레드에서 도는지 정한다
// SchedulingConfig 에 두지 않는 것은 그 파일을 네 트랙이 함께 고쳐서다, roomBroadcastExecutor 도 같은 이유로 여기 있다
@Configuration
@ConditionalOnProperty(name = "race.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class RoomSchedulingConfig {

    public static final String ROOM_STREAM = "roomStreamTaskScheduler";
    public static final String ROOM_CLOSE = "roomCloseTaskScheduler";

    // 하트비트는 구독마다 소켓에 ping 을 쏜다
    @Bean(ROOM_STREAM)
    public ThreadPoolTaskScheduler roomStreamTaskScheduler() {
        return threadPool("room-stream-");
    }

    // 마감된 방 끊기는 DB 를 읽는다, 하트비트와 같은 스레드에 두면 한쪽 지연이 다른 쪽을 민다
    @Bean(ROOM_CLOSE)
    public ThreadPoolTaskScheduler roomCloseTaskScheduler() {
        return threadPool("room-close-");
    }

    private static ThreadPoolTaskScheduler threadPool(String namePrefix) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(namePrefix);

        return scheduler;
    }
}
