package com.softeer.race.deal.application;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.deal.domain.Deal;
import com.softeer.race.deal.domain.DealRepository;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 낙찰된 경매로 거래를 연다
 * <p>
 * <b>호출자의 트랜잭션에 참여한다</b> (REQUIRES_NEW 를 쓰지 않는다). 낙찰이 롤백되면 거래도
 * 없어야 하고, 거래 없이 확정된 낙찰도 없어야 한다.
 */
@Service
@RequiredArgsConstructor
public class DealCreator {

    private final DealRepository dealRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;

    /**
     * @param auction 호출자가 이미 잠그고 확정한 경매
     * @param buyer   낙찰자, 유찰이면 호출하지 않는다
     */
    @Transactional
    public Deal create(Auction auction, User buyer, LocalDateTime now) {
        // 판매자가 없는 경매는 사용자가 고칠 수 있는 문제가 아니라 데이터가 깨진 것이다
        long sellerId = auctionRepository.findSellerIdById(auction.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "낙찰된 경매의 판매자를 찾을 수 없다, 경매 %d".formatted(auction.getId())));

        // 프록시로 참조만 건다, 거래가 쓰는 것은 식별자뿐이라 회원 조회가 추가로 나가지 않는다
        User seller = userRepository.getReferenceById(sellerId);

        return dealRepository.save(Deal.start(auction, seller, buyer, auction.getCurrentPrice(), now));
    }
}