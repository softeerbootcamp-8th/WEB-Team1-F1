package com.softeer.race.auctionroom.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 경매방 현황을 실제로 소켓에 쓰는 일꾼이다. SchedulingConfig 에 두지 않는 것은 그 파일을 네 트랙이 함께 고쳐서다
@Configuration
public class RoomBroadcastConfig {

    // 느린 구독 하나가 일꾼 하나를 붙잡는다. 이 수보다 동시에 느린 사람이 많아지면 정상 구독자의
    // 화면 갱신이 성겨진다, 입찰 응답은 그때도 안 밀린다. 실측으로 느린 10에 일꾼 8이면 프레임의
    // 41% 만 나가고 일꾼 16이면 100% 나갔다.
    @Value("${race.room.broadcast-workers}")
    private int workers;

    @Bean
    public ThreadPoolTaskExecutor roomBroadcastExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 큐가 무한이면 최대 크기에 영영 못 닿는다, 실제로 도는 수는 기본 크기다
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);

        // 구독 하나에 작업이 하나만 떠서 실제 상한이 구독 수다, 유계로 잡으면 거절된 구독의 칸이 영영 안 비워진다
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix("room-broadcast-");

        // 종료는 기본 동작에 맡긴다, 컨텍스트가 닫히면 새 작업을 거부하고 돌던 작업만 끝낸 뒤 내려간다
        // 거부된 뒤에 오는 끝내기는 SseRoomSubscription 이 부른 자리에서 마저 돌린다
        return executor;
    }
}
