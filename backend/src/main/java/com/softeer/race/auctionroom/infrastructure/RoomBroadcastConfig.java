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

        // 큐가 사실상 안 차서 maxPoolSize 에는 닿지 않는다, 동시에 도는 수는 이 값이다
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);

        // 기본값과 같은 값이지만 적어 둔다, 여기를 유계로 바꾸면 포화 때 거절이 나고
        // 그 폴백이 입찰을 처리한 요청 스레드에서 배달을 돌려 느린 구독을 떼어 낸 이 설정이 부하에서만 무의미해진다
        // 쌓이는 양은 구독 수를 넘지 않는다, 사서함이 구독 하나에 작업 하나만 띄운다
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix("room-broadcast-");

        // 종료는 기본 동작에 맡긴다, 컨텍스트가 닫히면 새 작업을 거부하고 돌던 작업만 끝낸 뒤 내려간다
        // 거부된 뒤에 오는 끝내기는 SseRoomSubscription 이 부른 자리에서 마저 돌린다
        return executor;
    }
}
