package com.softeer.race.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// 주기 작업을 켜고, 어떤 작업이 어느 스레드에서 도는지를 여기서 정한다.
//
// 스케줄러를 둘로 가른 이유는 경매방 브로드캐스트가 소켓 쓰기이기 때문이다.
// 한 스레드를 나눠 쓰면 안 받아 가는 구독자 하나가 스윕을 붙잡는 동안 마감 확정이 통째로 밀린다.
//
// 여기에 TaskScheduler 빈을 둔 대가로 부트의 기본 taskScheduler 는 back off 한다.
// @ConditionalOnMissingBean 이 타입 조건이라 빈 이름을 바꿔도 마찬가지다.
// 그래서 새로 붙이는 @Scheduled 는 scheduler 를 반드시 명시해야 한다.
// 빠뜨리면 none is named 'taskScheduler' INFO 한 줄만 남기고 관리되지 않는 스레드에서 돈다.
//
// 테스트에서는 꺼 둔다, 배경 스레드가 도는 동안 쿼리 수 같은 계약을 재면 값이 흔들린다
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "race.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
    public static final String AUCTION_PROGRESS = "auctionProgressTaskScheduler";
    public static final String ROOM_STREAM = "roomStreamTaskScheduler";

    @Bean(AUCTION_PROGRESS)
    public ThreadPoolTaskScheduler auctionProgressTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = threadPool("auction-progress-");

        // 배포로 종료될 때 처리 중인 경매를 최대 2초까지 기다린다.
        // 그 안에 못 끝낸 건은 롤백되고 다음 기동의 첫 틱에 다시 잡히므로, 잘려도 상태가 어긋나지 않는다.
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(2);

        return scheduler;
    }

    @Bean(ROOM_STREAM)
    public ThreadPoolTaskScheduler roomStreamTaskScheduler() {
        return threadPool("room-stream-");
    }

    private ThreadPoolTaskScheduler threadPool(String namePrefix) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(namePrefix);

        return scheduler;
    }
}
