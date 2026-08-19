package com.softeer.race.auctionlist.infrastructure;

import com.softeer.race.auctionlist.application.AuctionListChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

// 내려갈 때 열린 구독을 서버가 끝내 준다, 안 그러면 톰캣이 진행 중인 응답을 제한 시간까지 기다린다
@Component
@RequiredArgsConstructor
public class AuctionListStreamShutdown implements SmartLifecycle {

    private final AuctionListChannel auctionListChannel;

    private volatile boolean running;

    // 정지는 단계가 큰 것부터 돈다. 톰캣의 우아한 종료가 이보다 낮은 단계라 그 대기가 시작되기 전에 끊는다
    @Override
    public int getPhase() {
        return SmartLifecycle.DEFAULT_PHASE;
    }

    @Override
    public void start() {
        running = true;
    }

    // 이 시점에는 배달 일꾼이 이미 멈춰 있어 끝내기가 거절되고, 그러면 구독이 부른 자리에서 마저 돌린다
    @Override
    public void stop() {
        auctionListChannel.closeAll();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
