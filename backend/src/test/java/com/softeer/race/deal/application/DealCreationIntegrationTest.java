package com.softeer.race.deal.application;

import com.softeer.race.auction.application.AuctionCloser;
import com.softeer.race.auction.application.AuctionStarter;
import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.deal.domain.Deal;
import com.softeer.race.deal.domain.DealRepository;
import com.softeer.race.deal.domain.DealStatus;
import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.support.TestClock;
import com.softeer.race.user.domain.Role;
import com.softeer.race.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 낙찰이 확정되면 거래가 생기는가
 * <p>
 * <b>경매는 시더로 세우고 종료는 프로덕션 경로로 부른다.</b> 거래 행을 직접 심으면 "언제 만들어져야
 * 하는가"를 테스트가 스스로 정해 버려서, 유찰에도 거래를 만드는 버그를 잡지 못한다.
 * <p>
 * <b>{@code @Transactional} 을 걸지 않는다.</b> 검증 대상이 "거래 생성이 실패하면 낙찰도 되돌아간다"라
 * 커밋 경계가 관측돼야 한다. 정리는 부모의 {@code @AfterEach} 가 맡는다.
 */
@DisplayName("낙찰 거래 생성 통합 테스트")
class DealCreationIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 9, 12, 0);

    // 시작 11:00 → 마감 11:20, 기준 시각에는 이미 마감이 지났다
    private static final LocalDateTime STARTED_AT = NOW.minusHours(1);

    // 시더는 마감 시각에 종료를 부른다, 거래가 열린 시각도 요청 시각이 아니라 그 시각이다
    private static final LocalDateTime CLOSED_AT = STARTED_AT.plusMinutes(20);

    private static final long START_PRICE = 30_000_000L;
    private static final long RAISE = 1_000_000L;

    @Autowired
    private AuctionStarter auctionStarter;

    @Autowired
    private AuctionCloser auctionCloser;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private DealRepository dealRepository;

    private User seller;
    private User alice;
    private User bob;

    @BeforeEach
    void seedUsers() {
        fixClockAt(NOW);

        seller = users.user("박판매", Role.GENERAL);
        alice = users.user("김앨리스", Role.DEALER);
        bob = users.user("이밥", Role.DEALER);
    }

    @Test
    @DisplayName("시나리오 1 : 낙찰로 끝나면 거래가 하나 생기고 양쪽 당사자와 낙찰가가 담긴다")
    void scenario1_CreatesDealOnSold() {
        // given : 앨리스가 먼저 내고 밥이 올렸다, 최신이자 최고가인 밥이 낙찰자다
        long auctionId = rooms.room(seller, STARTED_AT)
                .startPrice(START_PRICE)
                .bid(STARTED_AT.plusMinutes(5), alice, START_PRICE)
                .bid(STARTED_AT.plusMinutes(10), bob, START_PRICE + RAISE)
                .closed()
                .create();

        // then : 낙찰가는 경매를 다시 읽지 않아도 거래가 스스로 들고 있다
        assertThat(dealRepository.findAll()).singleElement().satisfies(deal -> {
            assertThat(deal.getAuction().getId()).isEqualTo(auctionId);
            assertThat(deal.getSeller().getId()).isEqualTo(seller.getId());
            assertThat(deal.getBuyer().getId()).isEqualTo(bob.getId());
            assertThat(deal.getFinalPrice()).isEqualTo(START_PRICE + RAISE);
            assertThat(deal.getStatus()).isEqualTo(DealStatus.BUYER_CONFIRM_PENDING);
            assertThat(deal.getStatusChangedAt()).isEqualTo(CLOSED_AT);
        });
    }

    @Test
    @DisplayName("시나리오 2 : 입찰 없이 끝나면 거래가 만들어지지 않는다")
    void scenario2_CreatesNothingOnFailedAuction() {
        // given : 입찰이 0건이라 유찰이다
        rooms.room(seller, STARTED_AT)
                .startPrice(START_PRICE)
                .closed()
                .create();

        // then : 살 사람이 없는 거래는 만들 수 없다
        assertThat(dealRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("시나리오 3 : 같은 경매를 두 번 종료해도 거래는 하나다")
    void scenario3_SecondCloseCreatesNoDeal() {
        // given : 시더가 이미 한 번 종료했다
        long auctionId = rooms.room(seller, STARTED_AT)
                .startPrice(START_PRICE)
                .bid(STARTED_AT.plusMinutes(5), bob, START_PRICE)
                .closed()
                .create();

        // when : 스케줄러가 같은 경매를 다시 잡았다고 가정한다
        TestClock.INSTANCE.runAt(NOW, () -> auctionCloser.close(auctionId));

        // then : 잠금 안의 재판정이 두 번째를 걸러, 유니크 제약까지 가지 않는다
        assertThat(dealRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("시나리오 4 : 거래 생성이 실패하면 낙찰 확정도 알림도 되돌아간다")
    void scenario4_DealFailureRollsBackClose() {
        // given : 마감은 지났고 종료 확정만 남았다
        long auctionId = rooms.room(seller, STARTED_AT)
                .startPrice(START_PRICE)
                .bid(STARTED_AT.plusMinutes(5), bob, START_PRICE)
                .create();

        // 시작 전이 없이는 종료 확정이 상태 검사에 막힌다
        TestClock.INSTANCE.runAt(STARTED_AT, () -> auctionStarter.start(auctionId));

        // 같은 경매에 거래를 미리 심어 둔다, auction_id 유니크 제약이 두 번째 생성을 거부한다
        Auction auction = auctionRepository.findById(auctionId).orElseThrow();
        dealRepository.save(Deal.start(auction, seller, alice, START_PRICE, NOW));

        // when
        Throwable thrown = catchThrowable(() ->
                TestClock.INSTANCE.runAt(NOW, () -> auctionCloser.close(auctionId)));

        // then 1 : 확정이 되돌아가 다음 주기에 다시 잡힌다
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(statusOf(auctionId)).isEqualTo("IN_PROGRESS");

        // then 2 : 거래 없이 낙찰 알림만 나가는 상태를 만들지 않는다
        assertThat(countRows("notification")).isZero();

        // then 3 : 실패한 생성이 절반만 남지 않는다
        assertThat(dealRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("시나리오 5 : 경매의 현재가가 바뀌어도 거래의 낙찰 금액은 흔들리지 않는다")
    void scenario5_FinalPriceIsIndependentOfAuction() {
        // given : 낙찰가 3천만원으로 거래가 열렸다
        long auctionId = rooms.room(seller, STARTED_AT)
                .startPrice(START_PRICE)
                .bid(STARTED_AT.plusMinutes(5), bob, START_PRICE)
                .closed()
                .create();

        // when : 경매 쪽 현재가만 바뀐다
        jdbcTemplate.update(
                "update auction set current_price = ? where id = ?", START_PRICE + RAISE, auctionId);

        // then : 거래가 경매에서 읽어 오는 구조라면 여기서 같이 흔들린다
        assertThat(dealRepository.findAll()).singleElement()
                .satisfies(deal -> assertThat(deal.getFinalPrice()).isEqualTo(START_PRICE));
    }

    private Long countRows(String table) {
        return jdbcTemplate.queryForObject("select count(*) from `" + table + "`", Long.class);
    }

    private String statusOf(long auctionId) {
        return jdbcTemplate.queryForObject(
                "select status from auction where id = ?", String.class, auctionId);
    }
}
