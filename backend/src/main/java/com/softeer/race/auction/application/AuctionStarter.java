package com.softeer.race.auction.application;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuctionStarter {

    private final AuctionRepository auctionRepository;
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
    }
}
