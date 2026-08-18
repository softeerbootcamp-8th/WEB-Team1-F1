package com.softeer.race.bid.application;

import com.softeer.race.bid.application.dto.BidPlaceInfo;
import com.softeer.race.bid.application.dto.BidPlaced;
import com.softeer.race.bid.exception.BidErrorCode;
import com.softeer.race.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 입찰 접수 앞단으로, 잠금 앞 거르기와 경매별 직렬화를 맡고 판정과 저장은 BidService 가 한다.
 */
@Service
@RequiredArgsConstructor
public class BidFacade {

    // 부하 실측에서 잠금 대기 최댓값이 1.9초였고, 발동 0회를 확인한 값이다 (#402 측정 원장).
    private static final long WAIT_MILLIS = 3_000L;

    private final AuctionBidGate auctionBidGate;
    private final AuctionLockRegistry auctionLockRegistry;
    private final BidService bidService;

    public BidPlaceInfo place(long auctionId, long bidderId, long amount) {
        auctionBidGate.rejectIfDoomed(auctionId, bidderId, amount);

        ReentrantLock lock = auctionLockRegistry.acquire(auctionId);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(WAIT_MILLIS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new BusinessException(BidErrorCode.BID_LOCK_TIMEOUT);
            }

            BidPlaced placed = bidService.place(auctionId, bidderId, amount);

            // 커밋은 위 호출이 반환하면서 끝났고 잠금은 아직 안 놓았다.
            auctionBidGate.record(auctionId, placed.snapshot());
            return placed.info();
        } catch (InterruptedException e) {
            // 인터럽트 상태를 지운 채 넘기면 상위에서 중단 신호를 잃는다.
            Thread.currentThread().interrupt();
            throw new BusinessException(BidErrorCode.BID_LOCK_TIMEOUT);
        } finally {
            // unlock 이 release 보다 먼저다. 반납을 먼저 하면 내가 아직 잠금을 쥔 채 항목이 지워질 수 있어, 새 요청이 다른 잠금을 만들어 같은 경매에 들고 들어온다.
            if (acquired) {
                lock.unlock();
            }
            auctionLockRegistry.release(auctionId);
        }
    }
}
