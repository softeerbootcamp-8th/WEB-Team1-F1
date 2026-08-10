package com.softeer.race.auctionlist.domain;

import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.vehicle.domain.Manufacturer;
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
 * 목록 조회 쿼리를 실제 MySQL 에서
 * <p>
 * 1. 그룹별 정렬
 * 저장된 status 가 아니라 기준 시각과의 비교로 갈리는지, 종료만 방향이 뒤집혀 있는지
 * <p>
 * 2. 커서
 * 기준 시각이 같은 경매를 id 로 이어 읽어 누락도 중복도 없는지
 * <p>
 * 3. 경계와 필터
 * 시작 시각이 기준 시각과 같은 경매가 한 그룹에만 잡히는지, 삭제된 경매글이 빠지는지
 * <p>
 * 4. 매핑
 * select new 가 세 테이블 값을 레코드에 채우는지, 입찰 전 null 가격도 담기는지
 */
@DisplayName("경매글 목록 조회 쿼리 테스트")
@Transactional
@Sql("/sql/auction-list-fixture.sql")
class AuctionListRepositoryTest extends IntegrationTestSupport {

    // 픽스처가 이 시각을 기준으로 세 그룹에 나뉘도록 짜여 있다
    private static final LocalDateTime SNAPSHOT_AT = LocalDateTime.of(2026, 8, 3, 12, 0, 0);

    private static final int UNLIMITED = 100;

    @Autowired
    private AuctionListRepository auctionListRepository;

    // ================= 진행중 =================

    @Test
    @DisplayName("진행중은 마감이 임박한 순서로 나온다")
    void live_ordersByEndTime() {
        // when
        List<AuctionListRow> rows = findLive(AuctionListGroup.LIVE, UNLIMITED);

        // then : 마감 12:10 → 12:15 → 12:15 → 12:20
        // 픽스처의 status 는 전부 SCHEDULED 인데도 시각으로 갈린다
        assertThat(ids(rows)).containsExactly(101L, 102L, 103L, 110L);
    }

    @Test
    @DisplayName("진행중은 요청한 개수만큼만 읽는다")
    void live_respectsLimit() {
        // when
        List<AuctionListRow> rows = findLive(AuctionListGroup.LIVE, 2);

        // then
        assertThat(ids(rows)).containsExactly(101L, 102L);
    }

    @Test
    @DisplayName("마감 시각이 같은 경매는 id 로 이어 읽어 누락되지 않는다")
    void live_breaksTieByAuctionId() {
        // given : 102번과 103번은 마감이 12:15 로 같다. 102번까지 읽은 상태
        LocalDateTime cursorSortAt = LocalDateTime.of(2026, 8, 3, 12, 15, 0);

        // when
        List<AuctionListRow> rows = auctionListRepository.findLivePage(
                SNAPSHOT_AT, cursorSortAt, 102L, UNLIMITED);

        // then : 시각이 같아도 id 가 뒤인 103번이 살아남는다
        // 시각만 비교했다면 103번이 사라지고, >= 로 비교했다면 102번이 다시 나온다
        assertThat(ids(rows)).containsExactly(103L, 110L);
    }

    // ================= 예정 =================

    @Test
    @DisplayName("예정은 시작이 임박한 순서로 나온다")
    void pending_ordersByStartTime() {
        // when
        List<AuctionListRow> rows = findPending(AuctionListGroup.PENDING, UNLIMITED);

        // then : 시작 12:30 → 13:00
        assertThat(ids(rows)).containsExactly(104L, 105L);
    }

    // ================= 종료 =================

    @Test
    @DisplayName("종료는 최근에 끝난 순서로 나온다")
    void ended_ordersByEndTimeDescending() {
        // when
        List<AuctionListRow> rows = findEnded(AuctionListGroup.ENDED, UNLIMITED);

        // then : 마감 11:20 → 10:20. 오름차순이었다면 방금 끝난 107번이 뒤로 밀린다
        assertThat(ids(rows)).containsExactly(107L, 106L);
    }

    @Test
    @DisplayName("종료도 커서로 이어 읽는다")
    void ended_continuesFromCursor() {
        // given : 107번(마감 11:20)까지 읽은 상태
        LocalDateTime cursorSortAt = LocalDateTime.of(2026, 8, 3, 11, 20, 0);

        // when : 내림차순이라 커서보다 앞선 시각을 찾는다
        List<AuctionListRow> rows = auctionListRepository.findEndedPage(
                SNAPSHOT_AT, cursorSortAt, 107L, UNLIMITED);

        // then
        assertThat(ids(rows)).containsExactly(106L);
    }

    // ================= 경계와 필터 =================

    @Test
    @DisplayName("시작 시각이 기준 시각과 같은 경매는 진행중에만 잡힌다")
    void startBoundary_belongsToLiveOnly() {
        // given : 110번은 시작 시각이 기준 시각(12:00)과 정확히 같다

        // when
        List<Long> live = ids(findLive(AuctionListGroup.LIVE, UNLIMITED));
        List<Long> pending = ids(findPending(AuctionListGroup.PENDING, UNLIMITED));
        List<Long> ended = ids(findEnded(AuctionListGroup.ENDED, UNLIMITED));

        // then : 두 그룹에 걸치지도, 어디에도 안 잡히지도 않는다
        assertThat(live).contains(110L);
        assertThat(pending).doesNotContain(110L);
        assertThat(ended).doesNotContain(110L);
    }

    @Test
    @DisplayName("세 그룹은 서로 겹치지 않고 대상 경매를 빠짐없이 덮는다")
    void groups_partitionAllAuctions() {
        // when
        List<Long> live = ids(findLive(AuctionListGroup.LIVE, UNLIMITED));
        List<Long> pending = ids(findPending(AuctionListGroup.PENDING, UNLIMITED));
        List<Long> ended = ids(findEnded(AuctionListGroup.ENDED, UNLIMITED));

        // then : 겹치는 경매가 없고, 삭제된 한 건을 뺀 여덟 건이 전부 잡힌다
        assertThat(live).doesNotContainAnyElementsOf(pending);
        assertThat(live).doesNotContainAnyElementsOf(ended);
        assertThat(pending).doesNotContainAnyElementsOf(ended);
        assertThat(live.size() + pending.size() + ended.size()).isEqualTo(8);
    }

    @Test
    @DisplayName("삭제된 경매글은 목록에 나오지 않는다")
    void excludesDeletedPosts() {
        // given : 108번(삭제)은 진행중 경매와 같은 시간대다

        // when
        List<AuctionListRow> rows = findLive(AuctionListGroup.LIVE, UNLIMITED);

        // then : 시간 조건만 보면 걸려야 하지만 삭제 시각에서 걸러진다
        assertThat(ids(rows)).doesNotContain(108L);
    }

    // ================= 매핑 =================

    @Test
    @DisplayName("카드에 필요한 값이 세 테이블에서 모두 채워진다")
    void mapsAllCardFields() {
        // when
        AuctionListRow row = findLive(AuctionListGroup.LIVE, 1).getFirst();

        // then 1 : 경매글에서
        assertThat(row.thumbnailUrl()).isEqualTo("https://cdn.race.dev/101.jpg");

        // then 2 : 차량에서. 제조사는 저장 문자열 그대로 담기고 enum 복원은 manufacturerType() 이 한다.
        // 차량 id 는 경매 id(101)와 일부러 다른 대역이다. 같으면 a.id 와 뒤바뀌어도 통과한다
        assertThat(row.vehicleId()).isEqualTo(901L);
        assertThat(row.manufacturer()).isEqualTo("HYUNDAI");
        assertThat(row.manufacturerType()).isEqualTo(Manufacturer.HYUNDAI);
        assertThat(row.model()).isEqualTo("아반떼 CN7");
        assertThat(row.modelYear()).isEqualTo(2022);
        assertThat(row.mileage()).isEqualTo(35000);

        // then 3 : 경매에서
        assertThat(row.startPrice()).isEqualTo(10000000L);
        assertThat(row.currentPrice()).isEqualTo(11000000L);
        assertThat(row.roomOpenAt()).isEqualTo(LocalDateTime.of(2026, 8, 3, 11, 20));
        assertThat(row.startTime()).isEqualTo(LocalDateTime.of(2026, 8, 3, 11, 50));
        assertThat(row.currentEndTime()).isEqualTo(LocalDateTime.of(2026, 8, 3, 12, 10));
    }

    @Test
    @DisplayName("나의 경매 쿼리도 카드 값을 같은 자리에 채운다")
    void myQueries_mapSameFields() {
        // given : 공개 목록은 네이티브, 나의 경매는 JPQL 이라 매핑 경로가 다르다.
        // 컬럼을 한쪽에만 추가하면 여기서 갈린다. 픽스처의 판매자는 100번이다

        // when
        AuctionListRow row = auctionListRepository.findMyLivePage(
                100L, SNAPSHOT_AT, AuctionListGroup.LIVE.startSortAt(),
                AuctionListGroup.LIVE.startAuctionId(), Limit.of(1)).getFirst();

        // then : 네이티브 쪽 매핑 테스트와 같은 값이 나와야 한다
        assertThat(row.auctionId()).isEqualTo(101L);
        assertThat(row.vehicleId()).isEqualTo(901L);
        assertThat(row.manufacturer()).isEqualTo("HYUNDAI");
        assertThat(row.model()).isEqualTo("아반떼 CN7");
    }

    @Test
    @DisplayName("입찰 전 경매는 현재가가 null 로 담긴다")
    void mapsNullCurrentPrice() {
        // given : 102번은 아직 입찰이 없다

        // when
        AuctionListRow row = findLive(AuctionListGroup.LIVE, 2).get(1);

        // then : 시작가로 대체하는 건 서비스의 몫이고 쿼리는 있는 그대로 담는다
        assertThat(row.auctionId()).isEqualTo(102L);
        assertThat(row.currentPrice()).isNull();
        assertThat(row.startPrice()).isEqualTo(38000000L);
    }

    // ================= 호출 =================
    // 그룹을 처음부터 읽을 때의 커서 시작값은 AuctionListGroup 이 안다

    private List<AuctionListRow> findLive(AuctionListGroup group, int limit) {
        return auctionListRepository.findLivePage(
                SNAPSHOT_AT, group.startSortAt(), group.startAuctionId(), limit);
    }

    private List<AuctionListRow> findPending(AuctionListGroup group, int limit) {
        return auctionListRepository.findPendingPage(
                SNAPSHOT_AT, group.startSortAt(), group.startAuctionId(), limit);
    }

    private List<AuctionListRow> findEnded(AuctionListGroup group, int limit) {
        return auctionListRepository.findEndedPage(
                SNAPSHOT_AT, group.startSortAt(), group.startAuctionId(), limit);
    }

    private List<Long> ids(List<AuctionListRow> rows) {
        return rows.stream().map(AuctionListRow::auctionId).toList();
    }
}
