package com.softeer.race.auctionroom.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 경매방 현황을 실제로 소켓에 쓰는 일꾼이다. SchedulingConfig 에 두지 않는 것은 그 파일을 네 트랙이 함께 고쳐서다
@Configuration
public class RoomBroadcastConfig {

    private static final int WORKERS = 8;

    @Bean
    public ThreadPoolTaskExecutor roomBroadcastExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 큐가 무한이면 최대 크기에 영영 못 닿는다, 실제로 도는 수는 기본 크기다
        executor.setCorePoolSize(WORKERS);
        executor.setMaxPoolSize(WORKERS);

        // 구독 하나에 작업이 하나만 떠서 실제 상한이 구독 수다, 유계로 잡으면 거절된 구독의 칸이 영영 안 비워진다
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix("room-broadcast-");
        executor.setWaitForTasksToCompleteOnShutdown(false);

        return executor;
    }
}
