package com.softeer.race.auction.domain;

import com.softeer.race.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄러가 처리할 경매를 고르는 두 조회를 실제 MySQL 에서
 * <p>
 * 1. 상태 필터
 * 시작 전이는 SCHEDULED 만, 종료는 IN_PROGRESS 만 가져가는지.
 * 특히 마감이 지났어도 SCHEDULED 면 종료 대상이 아니어야 한다 — 시작 전이가 먼저 올려줘야 한다
 * <p>
 * 2. 시각 경계
 * 기준 시각과 정확히 같은 경매가 잡히는지 (<=)
 * <p>
 * 3. 정렬과 상한
 * 오래 밀린 것부터 나오는지, Limit 을 넘겨 읽지 않는지
 * <p>
 * 조회가 now 를 인자로 받으므로 시계를 고정하지 않는다. 픽스처 시각과 SNAPSHOT_AT 만으로 결정된다
 */
@DisplayName("경매 상태 진행 대상 조회 테스트")
@Transactional
@Sql("/sql/auction-progress-fixture.sql")
class AuctionRepositoryTest extends IntegrationTestSupport {

    // 픽스처가 이 시각을 기준으로 갈리도록 짜여 있다
    private static final LocalDateTime SNAPSHOT_AT = LocalDateTime.of(2026, 8, 5, 12, 0, 0);

    private static final Limit UNLIMITED = Limit.of(100);

    @Autowired
    private AuctionRepository auctionRepository;

    // ================= 시작 전이 대상 =================

    @Test
    @DisplayName("시작 시각이 지난 예약 경매를 시작이 이른 순서로 가져온다")
    void startable_ordersByStartTime() {
        List<Long> ids = auctionRepository.findStartableIds(AuctionStatus.SCHEDULED, SNAPSHOT_AT, UNLIMITED);

        // 304(10:00) → 301(11:00) → 302(12:00 정각)
        assertThat(ids).containsExactly(304L, 301L, 302L);
    }

    @Test
    @DisplayName("시작 시각이 기준 시각과 같으면 대상이고 1초 뒤면 아니다")
    void startable_boundaryIsInclusive() {
        List<Long> included = auctionRepository.findStartableIds(AuctionStatus.SCHEDULED, SNAPSHOT_AT, UNLIMITED);
        List<Long> excluded = auctionRepository.findStartableIds(
                AuctionStatus.SCHEDULED, SNAPSHOT_AT.minusSeconds(1), UNLIMITED);

        assertThat(included).contains(302L);   // 시작 12:00:00
        assertThat(included).doesNotContain(303L); // 시작 12:00:01
        assertThat(excluded).doesNotContain(302L);
    }

    // 스케줄러가 오래 멈췄다 재개하면 시작과 마감이 함께 지나 있는 경매가 생긴다.
    // 여기서 진행 중으로 올려야 같은 주기의 종료 처리가 이어받는다.
    @Test
    @DisplayName("마감까지 지난 예약 경매도 시작 전이 대상이다")
    void startable_includesAlreadyExpired() {
        List<Long> ids = auctionRepository.findStartableIds(AuctionStatus.SCHEDULED, SNAPSHOT_AT, UNLIMITED);

        assertThat(ids).contains(304L);
    }

    @Test
    @DisplayName("이미 진행 중이거나 끝난 경매는 시작 전이 대상이 아니다")
    void startable_excludesOtherStatuses() {
        List<Long> ids = auctionRepository.findStartableIds(AuctionStatus.SCHEDULED, SNAPSHOT_AT, UNLIMITED);

        assertThat(ids).doesNotContain(305L, 306L, 307L, 308L, 309L);
    }

    @Test
    @DisplayName("시작 전이 대상은 요청한 개수만큼만 읽는다")
    void startable_respectsLimit() {
        List<Long> ids = auctionRepository.findStartableIds(AuctionStatus.SCHEDULED, SNAPSHOT_AT, Limit.of(2));

        assertThat(ids).containsExactly(304L, 301L);
    }

    // ================= 종료 대상 =================

    @Test
    @DisplayName("마감이 지난 진행 중 경매를 마감이 이른 순서로 가져온다")
    void closable_ordersByEndTime() {
        List<Long> ids = auctionRepository.findClosableIds(AuctionStatus.IN_PROGRESS, SNAPSHOT_AT, UNLIMITED);

        // 305(11:20) → 308(12:00 정각)
        assertThat(ids).containsExactly(305L, 308L);
    }

    @Test
    @DisplayName("마감이 기준 시각과 같으면 대상이고 1초 뒤면 아니다")
    void closable_boundaryIsInclusive() {
        List<Long> included = auctionRepository.findClosableIds(AuctionStatus.IN_PROGRESS, SNAPSHOT_AT, UNLIMITED);
        List<Long> excluded = auctionRepository.findClosableIds(
                AuctionStatus.IN_PROGRESS, SNAPSHOT_AT.minusSeconds(1), UNLIMITED);

        assertThat(included).contains(308L);        // 마감 12:00:00
        assertThat(included).doesNotContain(309L);  // 마감 12:00:01
        assertThat(excluded).doesNotContain(308L);
    }

    // 이 조회가 상태를 IN_PROGRESS 로 좁힌 대가다. 상태가 낡은 경매는 여기서 안 잡히고,
    // 같은 주기에서 시작 전이가 먼저 올려준 뒤에야 대상이 된다.
    @Test
    @DisplayName("마감이 지났어도 예약 상태면 종료 대상이 아니다")
    void closable_excludesScheduled() {
        List<Long> ids = auctionRepository.findClosableIds(AuctionStatus.IN_PROGRESS, SNAPSHOT_AT, UNLIMITED);

        assertThat(ids).doesNotContain(304L);
    }

    @Test
    @DisplayName("이미 낙찰되거나 유찰된 경매는 다시 종료 대상이 되지 않는다")
    void closable_excludesClosed() {
        List<Long> ids = auctionRepository.findClosableIds(AuctionStatus.IN_PROGRESS, SNAPSHOT_AT, UNLIMITED);

        assertThat(ids).doesNotContain(306L, 307L);
    }

    @Test
    @DisplayName("종료 대상은 요청한 개수만큼만 읽는다")
    void closable_respectsLimit() {
        List<Long> ids = auctionRepository.findClosableIds(AuctionStatus.IN_PROGRESS, SNAPSHOT_AT, Limit.of(1));

        assertThat(ids).containsExactly(305L);
    }
}
