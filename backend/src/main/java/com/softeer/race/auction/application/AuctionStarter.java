package com.softeer.race.auction.application;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auction.domain.AuctionStarted;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionStarter {

    private final AuctionRepository auctionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public void start(long auctionId) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new IllegalStateException(
                        "시작 후보로 뽑힌 경매를 찾을 수 없다, 경매 %d".formatted(auctionId)
                ));

        LocalDateTime now = LocalDateTime.now(clock);

        // 서버를 여러 대로 늘리면 인스턴스마다 같은 후보를 뽑는다.
        // 잠금을 얻고 보니 다른 인스턴스가 이미 올렸을 수 있어 여기서 한 번 더 본다.
        if (!auction.isStartableAt(now)) {
            return;
        }

        auction.start(now);

        // 전이가 실제로 일어났을 때만 알린다, 위에서 되돌아간 경우는 알릴 사건이 없다
        eventPublisher.publishEvent(new AuctionStarted(auctionId));
    }
}
