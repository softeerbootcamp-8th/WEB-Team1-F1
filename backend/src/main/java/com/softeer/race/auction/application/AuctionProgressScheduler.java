package com.softeer.race.auction.application;

import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.common.config.SchedulingConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuctionProgressScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuctionProgressScheduler.class);

    private static final long TICK_MILLIS = 500L;

    private static final Limit BATCH_LIMIT = Limit.of(100);

    private final AuctionRepository auctionRepository;
    private final AuctionStarter auctionStarter;
    private final AuctionCloser auctionCloser;
    private final Clock clock;

    @Scheduled(fixedDelay = TICK_MILLIS, scheduler = SchedulingConfig.AUCTION_PROGRESS)
    public void advanceAuctions() {
        // 경과 시간이라 주입된 Clock(벽시계)이 아니라 단조 시계를 쓴다
        long tickStartedAt = System.nanoTime();

        // 시작 전이가 통째로 실패해도 낙찰 확정은 막지 않는다.
        // 상태 표시용 전이가 확정을 가로막는 구조가 되면 안 된다.
        try {
            startDue();
        } catch (Exception e) {
            log.error("시작 전이 단계 실패", e);
        }
        closeDue();

        // 0.5초 주기라 매 틱을 남기면 로그가 이것만으로 찬다, 기준을 넘긴 주기만 남긴다
        long elapsedMillis = (System.nanoTime() - tickStartedAt) / 1_000_000L;
        if (elapsedMillis > TICK_MILLIS) {
            log.warn("경매 진행 틱 초과 {}ms", elapsedMillis);
        }
    }

    private void startDue() {
        List<Long> auctionIds = auctionRepository.findStartableIds(
                AuctionStatus.SCHEDULED, LocalDateTime.now(clock), BATCH_LIMIT);

        for (Long auctionId : auctionIds) {
            // 한 건의 실패가 같은 주기의 나머지를 막지 않는다.
            // 실패한 건의 상태가 그대로라 다음 주기에 다시 잡힌다.

            try {
                auctionStarter.start(auctionId);
            } catch (Exception e) {
                log.error("경매 시작 전이 실패, 경매 {}", auctionId, e);
            }
        }
    }

    private void closeDue() {
        List<Long> auctionIds = auctionRepository.findClosableIds(AuctionStatus.IN_PROGRESS, LocalDateTime.now(clock), BATCH_LIMIT);

        for (Long auctionId : auctionIds) {
            try {
                auctionCloser.close(auctionId);
            } catch (Exception e) {
                log.error("경매 종료 확정 실패, 경매 {}", auctionId, e);
            }
        }
    }

}
