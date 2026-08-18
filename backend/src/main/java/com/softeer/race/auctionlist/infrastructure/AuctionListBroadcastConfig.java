package com.softeer.race.auctionlist.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 목록 현황을 실제로 소켓에 쓰는 일꾼이다. 경매방 풀과 나눠 두어 느린 목록 구독자가 방 배달을 잡지 않게 한다
@Configuration
public class AuctionListBroadcastConfig {

    // 느린 구독 하나가 일꾼 하나를 붙잡는다. 이 수보다 동시에 느린 사람이 많아지면 남은 구독의 갱신이 성겨진다
    @Value("${race.list.broadcast-workers}")
    private int workers;

    @Bean
    public ThreadPoolTaskExecutor auctionListBroadcastExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 큐가 사실상 안 차서 maxPoolSize 에는 닿지 않는다, 동시에 도는 수는 이 값이다
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);

        // 기본값과 같은 값이지만 적어 둔다, 여기를 유계로 바꾸면 포화 때 거절이 나고
        // 그 폴백이 입찰을 처리한 요청 스레드에서 배달을 돌려 이 이슈가 부하에서만 되살아난다
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix("list-broadcast-");

        // 종료는 기본 동작에 맡긴다, 컨텍스트가 닫히면 새 작업을 거부하고 돌던 작업만 끝낸 뒤 내려간다
        // 거부된 뒤에 오는 끝내기는 SseAuctionListSubscriber 가 부른 자리에서 마저 돌린다
        return executor;
    }
}
