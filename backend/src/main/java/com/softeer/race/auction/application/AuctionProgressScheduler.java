package com.softeer.race.auction.application;

import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auction.domain.AuctionStartAlertSubscriptionRepository;
import com.softeer.race.auction.domain.AuctionStartAlertTarget;
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
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuctionProgressScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuctionProgressScheduler.class);

    private static final long TICK_MILLIS = 500L;

    private static final Limit BATCH_LIMIT = Limit.of(100);

    // 시작 알림은 신청 행 단위로 센다, 경매 단위로 세면 신청자가 많은 경매 하나가 한 주기를 통째로 쓴다
    private static final Limit ALERT_BATCH_LIMIT = Limit.of(100);

    // 아직 시작하지 않은 경매의 신청은 후보가 아니다, 남아 있는 신청의 대부분이 그쪽이라
    // 걸러내지 않으면 처리량이 보낼 것 없는 행으로 채워진다
    private static final Set<AuctionStatus> ALERT_TARGET_STATUSES =
            Set.of(AuctionStatus.IN_PROGRESS, AuctionStatus.ENDED, AuctionStatus.FAILED);

    private final AuctionRepository auctionRepository;
    private final AuctionStarter auctionStarter;
    private final AuctionCloser auctionCloser;
    private final AuctionStartAlertSubscriptionRepository alertSubscriptionRepository;
    private final AuctionStartAlertNotifier auctionStartAlertNotifier;
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
        // 상태 전이보다 뒤에 둔다. 이 스케줄러는 스레드가 하나라 발송이 길어지면 다음 틱이 밀리는데,
        // 밀려도 되는 것은 알림이지 시작·낙찰 확정이 아니다.
        try {
            notifyStartAlerts();
        } catch (Exception e) {
            log.error("시작 알림 발송 단계 실패", e);
        }

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

    private void notifyStartAlerts() {
        List<AuctionStartAlertTarget> targets =
                alertSubscriptionRepository.findTargets(ALERT_TARGET_STATUSES, ALERT_BATCH_LIMIT);

        // 500ms 주기라 빈 결과까지 남기면 로그가 이것만으로 찬다
        if (targets.isEmpty()) {
            return;
        }

        Map<Long, List<AuctionStartAlertTarget>> byAuction =
                AuctionStartAlertTarget.groupByAuction(targets);

        // 경매당 신청자 수를 남긴다, 팬아웃 비용을 다시 볼 때 필요한 숫자가 이것뿐이다
        log.info("시작 알림 발송 후보 {}건, 경매 {}개", targets.size(), byAuction.size());

        byAuction.forEach((auctionId, group) -> {
            // 한 경매의 실패가 같은 주기의 나머지를 막지 않는다.
            // 실패한 경매의 신청이 그대로 남아 다음 주기에 다시 잡힌다.
            try {
                auctionStartAlertNotifier.notifyStart(auctionId, group);
            } catch (Exception e) {
                log.error("시작 알림 발송 실패, 경매 {}", auctionId, e);
            }
        });
    }
}
