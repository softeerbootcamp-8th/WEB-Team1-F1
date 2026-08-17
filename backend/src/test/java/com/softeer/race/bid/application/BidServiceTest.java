package com.softeer.race.bid.application;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auctionpost.domain.AuctionPost;
import org.springframework.context.ApplicationEventPublisher;
import com.softeer.race.bid.domain.BidIncrementTable;
import com.softeer.race.bid.domain.BidPreCheck;
import com.softeer.race.bid.domain.BidPreCheckRepository;
import com.softeer.race.bid.domain.BidRepository;
import com.softeer.race.bid.domain.BidRule;
import com.softeer.race.bid.exception.BidErrorCode;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.notification.application.NotificationPublisher;
import com.softeer.race.user.domain.Role;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 잠금 경계를 기준으로 무엇이 언제 일어나는지
 * <p>
 * 통합 테스트는 Clock.fixed 를 쓰므로 시각이 흐르지 않는다. 그래서 acceptedAt 을 잠금 앞으로
 * 되돌려도 통합 시나리오가 전부 통과한다. 이 클래스만 그 위치를 고정한다.
 * <p>
 * 사전 판정이 잠금 앞에서 끝나는지도 거절 사유로는 보이지 않는다 — 잠금 안에서 걸러도 같은 코드가
 * 나가므로, findByIdForUpdate 를 불렀는지로만 확인된다. 둘 다 호출 위치가 검증 대상이라 단위 테스트로 둔다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("입찰 접수의 잠금 경계")
class BidServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 8, 3, 19, 0);
    private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 8, 3, 20, 30);
    // schedule()이 계산하는 마감 = START_TIME + 20분
    private static final LocalDateTime END_TIME = LocalDateTime.of(2026, 8, 3, 20, 50);

    // 마감 10초 전. 이 시점의 판정이라면 입찰이 성립하고 소프트 클로즈로 마감이 밀린다
    private static final LocalDateTime BEFORE_LOCK = END_TIME.minusSeconds(10);
    // 잠금을 기다리는 동안 흐른 시간. 깨어나 보면 마감이 30초 지나 있다
    private static final Duration LOCK_WAIT = Duration.ofSeconds(40);

    private static final long AUCTION_ID = 1L;
    private static final long BIDDER_ID = 11L;
    private static final long SELLER_ID = 12L;
    private static final long START_PRICE = 24_800_000L;
    private static final long INCREMENT = 50_000L;
    private static final String BIDDER_REAL_NAME = "김입찰";
    private static final Manufacturer MANUFACTURER = Manufacturer.HYUNDAI;
    private static final String VEHICLE_MODEL = "아반떼 CN7";

    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BidPreCheckRepository bidPreCheckRepository;
    @Mock
    private BidIncrementService bidIncrementService;
    @Mock
    private NotificationPublisher notificationPublisher;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private BidIncrementTable table;

    @Test
    @DisplayName("잠금을 기다리는 사이 마감된 경매는 입찰을 거절한다")
    void rejectsBidWhenAuctionClosesWhileWaitingForLock() {
        AdvancingClock clock = new AdvancingClock(BEFORE_LOCK);
        Auction auction = scheduledAuction();
        BidService bidService = bidService(clock);

        when(bidIncrementService.loadTable()).thenReturn(table);
        when(bidPreCheckRepository.find(AUCTION_ID, BIDDER_ID)).thenReturn(Optional.of(preCheck()));
        when(table.ruleFor(START_PRICE, null))
                .thenReturn(new BidRule(START_PRICE, INCREMENT, START_PRICE));
        // 잠금을 얻기까지 40초를 기다린 상황을 만든다
        when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenAnswer(invocation -> {
            clock.advance(LOCK_WAIT);
            return Optional.of(auction);
        });

        // acceptedAt 을 잠금 앞에서 찍으면 마감 10초 전으로 판정해 이 입찰이 성립해 버린다
        assertThatThrownBy(() -> bidService.place(AUCTION_ID, BIDDER_ID, START_PRICE))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).errorCode())
                .isEqualTo(BidErrorCode.AUCTION_NOT_LIVE);

        // 마감된 경매의 현재가와 마감 시각이 그대로인지까지 확인한다
        verify(bidRepository, never()).save(any());
        assertThat(auction.getCurrentPrice()).isNull();
        assertThat(auction.getCurrentEndTime()).isEqualTo(END_TIME);
        assertThat(auction.getExtensionCount()).isZero();
    }

    @Test
    @DisplayName("잠금을 곧바로 얻으면 마감 직전 입찰이 성립하고 마감이 연장된다")
    void acceptsClosingBidWhenLockIsFree() {
        AdvancingClock clock = new AdvancingClock(BEFORE_LOCK);
        Auction auction = scheduledAuction();
        BidService bidService = bidService(clock);

        when(bidIncrementService.loadTable()).thenReturn(table);
        when(bidPreCheckRepository.find(AUCTION_ID, BIDDER_ID)).thenReturn(Optional.of(preCheck()));
        when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));
        // 잠금 앞 사전 판정과 잠금 안 판정이 같은 인자로 두 번 부른다
        when(table.ruleFor(START_PRICE, null))
                .thenReturn(new BidRule(START_PRICE, INCREMENT, START_PRICE));
        when(userRepository.getReferenceById(BIDDER_ID)).thenReturn(bidder());
        when(bidRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        bidService.place(AUCTION_ID, BIDDER_ID, START_PRICE);

        // 위 케이스와 같은 경매·같은 출발 시각인데 결과가 갈리는 근거는 잠금 대기 시간뿐이다
        assertThat(auction.getCurrentPrice()).isEqualTo(START_PRICE);
        assertThat(auction.getCurrentEndTime()).isEqualTo(BEFORE_LOCK.plusSeconds(30));
        assertThat(auction.getExtensionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("최소 금액에 못 미치는 입찰은 잠금을 잡기 전에 거절한다")
    void rejectsAmountBelowMinimumBeforeLock() {
        BidService bidService = bidService(new AdvancingClock(BEFORE_LOCK));

        when(bidIncrementService.loadTable()).thenReturn(table);
        when(bidPreCheckRepository.find(AUCTION_ID, BIDDER_ID))
                .thenReturn(Optional.of(new BidPreCheck(
                        Role.DEALER, BIDDER_REAL_NAME, SELLER_ID, MANUFACTURER, VEHICLE_MODEL,
                        START_PRICE, START_PRICE, START_TIME, END_TIME)));
        when(table.ruleFor(START_PRICE, START_PRICE))
                .thenReturn(new BidRule(START_PRICE, INCREMENT, START_PRICE + INCREMENT));

        assertThatThrownBy(() -> bidService.place(AUCTION_ID, BIDDER_ID, START_PRICE))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).errorCode())
                .isEqualTo(BidErrorCode.BID_AMOUNT_TOO_LOW);

        // 거절 사유만 보면 잠금 안에서 걸러도 통과한다, 이 검증이 조기 거절의 유일한 증거다
        verify(auctionRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("마감된 경매에 낮은 금액이 오면 금액이 아니라 마감을 사유로 거절한다")
    void reportsNotLiveBeforeAmountOnClosedAuction() {
        Auction auction = scheduledAuction();
        BidService bidService = bidService(new AdvancingClock(END_TIME.plusSeconds(1)));

        when(bidPreCheckRepository.find(AUCTION_ID, BIDDER_ID))
                .thenReturn(Optional.of(new BidPreCheck(
                        Role.DEALER, BIDDER_REAL_NAME, SELLER_ID, MANUFACTURER, VEHICLE_MODEL,
                        START_PRICE, START_PRICE, START_TIME, END_TIME)));
        when(auctionRepository.findByIdForUpdate(AUCTION_ID)).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> bidService.place(AUCTION_ID, BIDDER_ID, START_PRICE))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).errorCode())
                .isEqualTo(BidErrorCode.AUCTION_NOT_LIVE);
    }

    @Test
    @DisplayName("회원이 없으면 경매를 잠그기 전에 거절한다")
    void rejectsMissingBidderBeforeLock() {
        BidService bidService = bidService(new AdvancingClock(BEFORE_LOCK));

        when(bidPreCheckRepository.find(AUCTION_ID, BIDDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bidService.place(AUCTION_ID, BIDDER_ID, START_PRICE))
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).errorCode())
                .isEqualTo(BidErrorCode.BIDDER_NOT_FOUND);

        verify(auctionRepository, never()).findByIdForUpdate(anyLong());
    }

    private BidService bidService(Clock clock) {
        return new BidService(
                auctionRepository, bidRepository, userRepository, bidPreCheckRepository,
                bidIncrementService, notificationPublisher, eventPublisher, clock);
    }

    // 입찰이 없는 진행 중 경매, 입찰자는 판매자도 평가사도 아니다
    private BidPreCheck preCheck() {
        return new BidPreCheck(
                Role.DEALER, BIDDER_REAL_NAME, SELLER_ID, MANUFACTURER, VEHICLE_MODEL,
                START_PRICE, null, START_TIME, END_TIME);
    }

    private Auction scheduledAuction() {
        return Auction.schedule(AuctionPost.create(null, PUBLISHED_AT), START_PRICE, START_TIME);
    }

    private User bidder() {
        return User.create("bidder", "bidder@race.dev", "pw", "김입찰", "01000000011", Role.DEALER);
    }

    /** 호출할 때가 아니라 advance 를 부를 때만 흐르는 시계, 잠금 대기를 흉내 낸다 */
    private static final class AdvancingClock extends Clock {

        private Instant instant;

        private AdvancingClock(LocalDateTime start) {
            this.instant = start.atZone(KST).toInstant();
        }

        void advance(Duration elapsed) {
            instant = instant.plus(elapsed);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return KST;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
