package com.softeer.race.support.seed;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auctionpost.domain.AuctionPost;
import com.softeer.race.auctionpost.domain.AuctionPostRepository;
import com.softeer.race.bid.domain.Bid;
import com.softeer.race.bid.domain.BidRepository;
import com.softeer.race.support.TestClock;
import com.softeer.race.user.domain.User;
import com.softeer.race.vehicle.domain.*;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 경매방 데이터를 등록과 입찰이라는 실제 경로로 세운다
 */
// 도메인이 만들 수 없는 상태는 여기서도 만들 수 없다 — 낙찰 확정과 게시글 삭제가 그렇다
@RequiredArgsConstructor
public class AuctionRoomSeeder {

    // 번호판의 유일 제약을 피한다, 테이블을 비워도 되돌아가지 않아 앞 테스트가 쓴 값과 겹치지 않는다
    private static final AtomicLong SERIAL = new AtomicLong();

    // 주행거리는 제원이 아니라 신고값이라 spec 이 아니라 Vehicle.create 로 직접 넘긴다
    private static final int DEFAULT_MILEAGE = 35_000;

    private static final long DEFAULT_ESTIMATED_PRICE = 15_000_000L;
    private static final long DEFAULT_START_PRICE = 10_000_000L;

    private final VehicleRepository vehicleRepository;
    private final AuctionPostRepository auctionPostRepository;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;

    /**
     * 시작 시각을 정해 경매를 예약한다, 그 시각 기준으로 개장·마감이 도메인 규칙대로 잡힌다
     */
    public RoomBuilder room(User seller, LocalDateTime startAt) {
        return new RoomBuilder(seller, startAt);
    }

    public final class RoomBuilder {

        private final User seller;
        private final LocalDateTime startAt;
        private final List<PlacedBid> bids = new ArrayList<>();

        private String model = "아반떼 CN7";
        private String thumbnailUrl = "https://cdn.race.dev/avante.jpg";
        private long startPrice = DEFAULT_START_PRICE;

        private RoomBuilder(User seller, LocalDateTime startAt) {
            this.seller = seller;
            this.startAt = startAt;
        }

        public RoomBuilder model(String model) {
            this.model = model;
            return this;
        }

        public RoomBuilder thumbnailUrl(String thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        public RoomBuilder startPrice(long startPrice) {
            this.startPrice = startPrice;
            return this;
        }

        /**
         * 그 시각에 들어온 입찰 한 건, 넣은 순서가 곧 호가창 순서다
         */
        public RoomBuilder bid(LocalDateTime at, User bidder, long amount) {
            bids.add(new PlacedBid(at, bidder, amount));
            return this;
        }

        /**
         * 지금까지 지정한 값으로 경매를 만들고 식별자를 돌려준다
         */
        public long create() {
            // 시작 시각은 발행보다 최소 리드타임만큼 뒤여야 한다, 발행을 하루 앞에 둔다
            LocalDateTime publishedAt = startAt.minusDays(1);

            Auction auction = TestClock.INSTANCE.at(publishedAt, () -> {
                Vehicle vehicle = vehicleRepository.save(Vehicle.create(seller, spec(), DEFAULT_MILEAGE, DEFAULT_ESTIMATED_PRICE));
                AuctionPost post = auctionPostRepository.save(AuctionPost.create(vehicle, thumbnailUrl, publishedAt));

                return auctionRepository.save(Auction.schedule(post, startPrice, startAt));
            });

            for (PlacedBid placed : bids) {
                TestClock.INSTANCE.at(placed.at(), () -> {
                    auction.acceptBid(placed.amount(), placed.at());
                    bidRepository.save(Bid.place(auction, placed.bidder(), placed.amount()));

                    return auctionRepository.save(auction);
                });
            }

            return auction.getId();
        }

        private VehicleSpec spec() {
            long serial = SERIAL.incrementAndGet();

            return new VehicleSpec(
                    "%02d가%04d".formatted(serial % 100, serial % 10000),
                    seller.getRealName(),
                    Manufacturer.HYUNDAI,
                    model,
                    2022,
                    FuelType.GASOLINE,
                    Transmission.AUTOMATIC,
                    DEFAULT_ESTIMATED_PRICE,
                    thumbnailUrl);
        }
    }

    private record PlacedBid(LocalDateTime at, User bidder, long amount) {
    }
}