package com.softeer.race.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// 주기 작업을 켜고, 어떤 작업이 어느 스레드에서 도는지를 여기서 정한다.
//
// 스케줄러를 작업별로 가른 이유는 소켓 쓰기·알림 팬아웃과 시각 판정을 한 스레드에 섞을 수 없기 때문이다.
// 나눠 쓰면 안 받아 가는 구독자 하나가 스레드를 붙잡는 동안 마감 확정이나 다른 채널 청소가 통째로 밀린다.
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
    public static final String AUCTION_START_ALERT = "auctionStartAlertTaskScheduler";
    public static final String ROOM_STREAM = "roomStreamTaskScheduler";
    public static final String LIST_STREAM = "listStreamTaskScheduler";
    public static final String NOTIFICATION_STREAM = "notificationStreamTaskScheduler";

    @Bean(AUCTION_PROGRESS)
    public ThreadPoolTaskScheduler auctionProgressTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = threadPool("auction-progress-");

        // 배포로 종료될 때 처리 중인 경매를 최대 2초까지 기다린다.
        // 그 안에 못 끝낸 건은 롤백되고 다음 기동의 첫 틱에 다시 잡히므로, 잘려도 상태가 어긋나지 않는다.
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(2);

        return scheduler;
    }

    @Bean(AUCTION_START_ALERT)
    public ThreadPoolTaskScheduler auctionStartAlertTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = threadPool("auction-start-alert-");

        // 알림 저장과 신청 삭제가 한 트랜잭션이라, 종료 중인 작업에는 짧은 완료 기회를 준다.
        // 시간 안에 끝나지 못하면 롤백되고 다음 기동의 첫 알림 틱에서 다시 잡힌다.
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

    @Bean(LIST_STREAM)
    public ThreadPoolTaskScheduler listStreamTaskScheduler() {
        return threadPool("list-stream-");
    }

    @Bean(NOTIFICATION_STREAM)
    public ThreadPoolTaskScheduler notificationStreamTaskScheduler() {
        return threadPool("notification-stream-");
    }
}
