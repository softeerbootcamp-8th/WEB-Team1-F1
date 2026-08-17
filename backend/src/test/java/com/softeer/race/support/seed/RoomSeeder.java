package com.softeer.race.support.seed;

import com.softeer.race.auction.application.AuctionCloser;
import com.softeer.race.auction.application.AuctionStarter;
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
// 도메인이 만들 수 없는 상태는 여기서도 만들 수 없다 — 게시글 삭제가 그렇다
@RequiredArgsConstructor
public class RoomSeeder {

    // 번호판의 유일 제약을 피한다, 테이블을 비워도 되돌아가지 않아 앞 테스트가 쓴 값과 겹치지 않는다
    private static final AtomicLong SERIAL = new AtomicLong();

    // 주행거리는 제원이 아니라 평가사 실측값이라 spec 이 아니라 completeDiagnosis 로 넘긴다
    private static final int DEFAULT_MILEAGE = 35_000;

    private static final long DEFAULT_ESTIMATED_PRICE = 15_000_000L;
    private static final long DEFAULT_START_PRICE = 10_000_000L;

    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final VehicleKeywordTagRepository vehicleKeywordTagRepository;
    private final AuctionPostRepository auctionPostRepository;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuctionStarter auctionStarter;
    private final AuctionCloser auctionCloser;

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
        // 프로덕션은 첫 장을 대표로 삼고 나머지를 순서대로 저장한다, 그 규칙을 그대로 밟는다
        private String[] photos = {
                "https://cdn.race.dev/avante-1.jpg",
                "https://cdn.race.dev/avante-2.jpg",
                "https://cdn.race.dev/avante-3.jpg",
        };
        // 사진과 확장자·경로가 다르다, 둘 다 String 이라 자리가 바뀌어도 컴파일되므로 기본값을 갈라 둔다
        private String diagnosticReportUrl = "https://cdn.race.dev/avante-report.pdf";
        // 기본으로 심지 않는다, 심어 두면 키워드를 지정하지 않은 테스트의 기대값이 조용히 바뀐다
        private VehicleKeyword[] keywords = {};
        private long startPrice = DEFAULT_START_PRICE;
        private boolean closed;

        private RoomBuilder(User seller, LocalDateTime startAt) {
            this.seller = seller;
            this.startAt = startAt;
        }

        public RoomBuilder model(String model) {
            this.model = model;
            return this;
        }

        public RoomBuilder photos(String... photos) {
            this.photos = photos;
            return this;
        }

        public RoomBuilder diagnosticReportUrl(String diagnosticReportUrl) {
            this.diagnosticReportUrl = diagnosticReportUrl;
            return this;
        }

        public RoomBuilder keywords(VehicleKeyword... keywords) {
            this.keywords = keywords;
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
         * 마감 시각에 종료 확정까지 진행한다, 입찰이 없으면 유찰이다
         */
        public RoomBuilder closed() {
            this.closed = true;
            return this;
        }

        /**
         * 지금까지 지정한 값으로 경매를 만들고 식별자를 돌려준다
         */
        public long create() {
            // 시작 시각은 발행보다 최소 리드타임만큼 뒤여야 한다, 발행을 하루 앞에 둔다
            LocalDateTime publishedAt = startAt.minusDays(1);

            Auction auction = TestClock.INSTANCE.at(publishedAt, () -> {
                // 프로덕션은 방문견적으로 빈 차량을 만들고 평가사가 결과를 내며 채운다, 그 순서를 그대로 밟는다
                Vehicle vehicle = Vehicle.pendingDiagnosis(seller, spec());
                vehicle.completeDiagnosis(DEFAULT_MILEAGE, DEFAULT_ESTIMATED_PRICE, photos[0], diagnosticReportUrl);
                vehicleRepository.save(vehicle);
                for (int order = 0; order < photos.length; order++) {
                    vehicleImageRepository.save(VehicleImage.create(vehicle, photos[order], order));
                }
                for (VehicleKeyword keyword : keywords) {
                    vehicleKeywordTagRepository.save(VehicleKeywordTag.create(vehicle, keyword));
                }
                AuctionPost post = auctionPostRepository.save(AuctionPost.create(vehicle, publishedAt));

                return auctionRepository.save(Auction.schedule(post, startPrice, startAt));
            });

            for (PlacedBid placed : bids) {
                TestClock.INSTANCE.at(placed.at(), () -> {
                    auction.acceptBid(placed.bidder(), placed.amount(), placed.at());
                    bidRepository.save(Bid.place(auction, placed.bidder(), placed.amount()));

                    return auctionRepository.save(auction);
                });
            }

            if (closed) {
                closeAt(auction);
            }

            return auction.getId();
        }

        // 스케줄러가 부르는 것을 그대로 부른다, 최고 입찰자 판정도 프로덕션 것을 쓴다
        // 시작 전이 없이는 종료 확정이 상태 검사에 막힌다
        private void closeAt(Auction auction) {
            long auctionId = auction.getId();

            TestClock.INSTANCE.runAt(auction.getStartTime(), () -> auctionStarter.start(auctionId));
            TestClock.INSTANCE.runAt(auction.getCurrentEndTime(), () -> auctionCloser.close(auctionId));
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
                    // 카탈로그 도판 자리다, 평가사가 찍은 실물 사진과 다른 값이라 섞지 않는다
                    null);
        }
    }

    private record PlacedBid(LocalDateTime at, User bidder, long amount) {
    }
}