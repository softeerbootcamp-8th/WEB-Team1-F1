package com.softeer.race.auctionlist.domain;

import com.softeer.race.support.IntegrationTestSupport;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 세 테이블 값이 레코드에 채워지는지, 입찰 전 null 가격도 담기는지
 * <p>
 * 5. 차량 조건 필터
 * 조건이 있는 것만 SQL 에 붙는 동적 조립이 단독·조합·커서와 함께 맞게 거르는지
 */
@DisplayName("경매글 목록 조회 쿼리 테스트")
@Transactional
@Sql("/sql/auction-list-fixture.sql")
class AuctionListRepositoryTest extends IntegrationTestSupport {

    // 픽스처가 이 시각을 기준으로 세 그룹에 나뉘도록 짜여 있다
    private static final LocalDateTime SNAPSHOT_AT = LocalDateTime.of(2026, 8, 3, 12, 0, 0);

    private static final int UNLIMITED = 100;

    private static final AuctionListFilter NO_FILTER = AuctionListFilter.none();

    @Autowired
    private AuctionListRepository auctionListRepository;

    // ================= 진행중 =================

    @Test
    @DisplayName("진행중은 마감이 임박한 순서로 나온다")
    void live_ordersByEndTime() {
        // when
        List<AuctionListRow> rows = find(AuctionListGroup.LIVE, UNLIMITED);

        // then : 마감 12:10 → 12:15 → 12:15 → 12:20
        // 픽스처의 status 는 전부 SCHEDULED 인데도 시각으로 갈린다
        assertThat(ids(rows)).containsExactly(101L, 102L, 103L, 110L);
    }

    @Test
    @DisplayName("진행중은 요청한 개수만큼만 읽는다")
    void live_respectsLimit() {
        // when
        List<AuctionListRow> rows = find(AuctionListGroup.LIVE, 2);

        // then
        assertThat(ids(rows)).containsExactly(101L, 102L);
    }

    @Test
    @DisplayName("마감 시각이 같은 경매는 id 로 이어 읽어 누락되지 않는다")
    void live_breaksTieByAuctionId() {
        // given : 102번과 103번은 마감이 12:15 로 같다. 102번까지 읽은 상태
        LocalDateTime cursorSortAt = LocalDateTime.of(2026, 8, 3, 12, 15, 0);

        // when
        List<AuctionListRow> rows = auctionListRepository.findPage(
                AuctionListGroup.LIVE, NO_FILTER, null, SNAPSHOT_AT, cursorSortAt, 102L, UNLIMITED);

        // then : 시각이 같아도 id 가 뒤인 103번이 살아남는다
        // 시각만 비교했다면 103번이 사라지고, >= 로 비교했다면 102번이 다시 나온다
        assertThat(ids(rows)).containsExactly(103L, 110L);
    }

    // ================= 예정 =================

    @Test
    @DisplayName("예정은 시작이 임박한 순서로 나온다")
    void pending_ordersByStartTime() {
        // when
        List<AuctionListRow> rows = find(AuctionListGroup.PENDING, UNLIMITED);

        // then : 시작 12:30 → 13:00
        assertThat(ids(rows)).containsExactly(104L, 105L);
    }

    // ================= 종료 =================

    @Test
    @DisplayName("종료는 최근에 끝난 순서로 나온다")
    void ended_ordersByEndTimeDescending() {
        // when
        List<AuctionListRow> rows = find(AuctionListGroup.ENDED, UNLIMITED);

        // then : 마감 11:20 → 10:20. 오름차순이었다면 방금 끝난 107번이 뒤로 밀린다
        assertThat(ids(rows)).containsExactly(107L, 106L);
    }

    @Test
    @DisplayName("종료도 커서로 이어 읽는다")
    void ended_continuesFromCursor() {
        // given : 107번(마감 11:20)까지 읽은 상태
        LocalDateTime cursorSortAt = LocalDateTime.of(2026, 8, 3, 11, 20, 0);

        // when : 내림차순이라 커서보다 앞선 시각을 찾는다
        List<AuctionListRow> rows = auctionListRepository.findPage(
                AuctionListGroup.ENDED, NO_FILTER, null, SNAPSHOT_AT, cursorSortAt, 107L, UNLIMITED);

        // then
        assertThat(ids(rows)).containsExactly(106L);
    }

    // ================= 경계와 필터 =================

    @Test
    @DisplayName("시작 시각이 기준 시각과 같은 경매는 진행중에만 잡힌다")
    void startBoundary_belongsToLiveOnly() {
        // given : 110번은 시작 시각이 기준 시각(12:00)과 정확히 같다

        // when
        List<Long> live = ids(find(AuctionListGroup.LIVE, UNLIMITED));
        List<Long> pending = ids(find(AuctionListGroup.PENDING, UNLIMITED));
        List<Long> ended = ids(find(AuctionListGroup.ENDED, UNLIMITED));

        // then : 두 그룹에 걸치지도, 어디에도 안 잡히지도 않는다
        assertThat(live).contains(110L);
        assertThat(pending).doesNotContain(110L);
        assertThat(ended).doesNotContain(110L);
    }

    @Test
    @DisplayName("세 그룹은 서로 겹치지 않고 대상 경매를 빠짐없이 덮는다")
    void groups_partitionAllAuctions() {
        // when
        List<Long> live = ids(find(AuctionListGroup.LIVE, UNLIMITED));
        List<Long> pending = ids(find(AuctionListGroup.PENDING, UNLIMITED));
        List<Long> ended = ids(find(AuctionListGroup.ENDED, UNLIMITED));

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
        List<AuctionListRow> rows = find(AuctionListGroup.LIVE, UNLIMITED);

        // then : 시간 조건만 보면 걸려야 하지만 삭제 시각에서 걸러진다
        assertThat(ids(rows)).doesNotContain(108L);
    }

    // ================= 차량 조건 필터 =================

    @Test
    @DisplayName("제조사 필터는 해당 제조사만 남긴다")
    void filtersByManufacturer() {
        // when : 진행중 네 대 중 기아는 102(쏘렌토)·103(EV6)이다
        List<AuctionListRow> rows = find(AuctionListGroup.LIVE, manufacturer(Manufacturer.KIA), UNLIMITED);

        // then : 정렬은 필터와 무관하게 유지된다
        assertThat(ids(rows)).containsExactly(102L, 103L);
    }

    @Test
    @DisplayName("연료 다중 선택은 어느 하나와 맞으면 남는다")
    void filtersByFuelTypesAsOr() {
        // given : 진행중에서 하이브리드는 102, 전기는 103 이다
        AuctionListFilter filter = new AuctionListFilter(
                null, List.of(FuelType.HYBRID, FuelType.ELECTRIC), null, null, null, null, null, null, null);

        // when
        List<AuctionListRow> rows = find(AuctionListGroup.LIVE, filter, UNLIMITED);

        // then : AND 로 묶었다면 두 연료를 다 가진 차가 없어 빈 목록이 된다
        assertThat(ids(rows)).containsExactly(102L, 103L);
    }

    @Test
    @DisplayName("변속기 필터는 해당 변속기만 남긴다")
    void filtersByTransmission() {
        // given : 수동변속은 종료 그룹의 캠리(107)뿐이다
        AuctionListFilter filter = new AuctionListFilter(
                null, null, Transmission.MANUAL, null, null, null, null, null, null);

        // when
        List<AuctionListRow> rows = find(AuctionListGroup.ENDED, filter, UNLIMITED);

        // then
        assertThat(ids(rows)).containsExactly(107L);
    }

    @Test
    @DisplayName("주행거리 범위는 경계값을 포함한다")
    void mileageRangeIncludesBoundary() {
        // given : 103번 차량의 주행거리 27800 을 양쪽 경계로 지정한다
        AuctionListFilter filter = new AuctionListFilter(null, null, null, 27800, 27800, null, null, null, null);

        // when
        List<AuctionListRow> rows = find(AuctionListGroup.LIVE, filter, UNLIMITED);

        // then : 초과·미만으로 비교했다면 빈 목록이 된다
        assertThat(ids(rows)).containsExactly(103L);
    }

    @Test
    @DisplayName("연식 범위는 그 해에 등록된 차량만 남긴다")
    void filtersByModelYearRange() {
        // given : 진행중에서 2022년식은 101(아반떼)·103(EV6)이고 102 는 2023년식이다
        AuctionListFilter filter = new AuctionListFilter(null, null, null, null, null, 2022, 2022, null, null);

        // when
        List<AuctionListRow> rows = find(AuctionListGroup.LIVE, filter, UNLIMITED);

        // then
        assertThat(ids(rows)).containsExactly(101L, 103L);
    }

    @Test
    @DisplayName("가격 범위는 화면에 보이는 가격 기준으로 거른다")
    void filtersByDisplayPriceRange() {
        // given : 진행중의 표시가는 101=1100만(현재가), 102=3800만, 103=3400만, 110=3900만(시작가)이다
        AuctionListFilter filter = new AuctionListFilter(null, null, null, null, null, null, null, 38000000L, null);

        // when
        List<AuctionListRow> rows = find(AuctionListGroup.LIVE, filter, UNLIMITED);

        // then : 3800만 이상은 102·110 이고 경계값이 포함된다
        assertThat(ids(rows)).containsExactly(102L, 110L);
    }

    @Test
    @DisplayName("입찰이 있으면 시작가가 아니라 현재가로 거른다")
    void priceFilterPrefersCurrentPrice() {
        // given : 101번은 시작가 1000만, 현재가 1100만이다
        AuctionListFilter filter = new AuctionListFilter(null, null, null, null, null, null, null, null, 10000000L);

        // when
        List<AuctionListRow> rows = find(AuctionListGroup.LIVE, filter, UNLIMITED);

        // then : 시작가로 걸렀다면 101 이 남는다
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("여러 조건을 함께 걸면 전부 만족하는 차량만 남는다")
    void combinesFiltersWithAnd() {
        // given : 기아이면서 전기차는 103 하나다
        AuctionListFilter filter = new AuctionListFilter(
                Manufacturer.KIA, List.of(FuelType.ELECTRIC), null, null, null, null, null, null, null);

        // when
        List<AuctionListRow> rows = find(AuctionListGroup.LIVE, filter, UNLIMITED);

        // then : OR 로 묶였다면 102(기아 하이브리드)도 살아남는다
        assertThat(ids(rows)).containsExactly(103L);
    }

    @Test
    @DisplayName("필터가 걸린 채로도 커서로 이어 읽는다")
    void filterKeepsCursorPaging() {
        // given : 기아 필터로 102번(마감 12:15)까지 읽은 상태
        LocalDateTime cursorSortAt = LocalDateTime.of(2026, 8, 3, 12, 15, 0);

        // when
        List<AuctionListRow> rows = auctionListRepository.findPage(
                AuctionListGroup.LIVE, manufacturer(Manufacturer.KIA), null,
                SNAPSHOT_AT, cursorSortAt, 102L, UNLIMITED);

        // then
        assertThat(ids(rows)).containsExactly(103L);
    }

    @Test
    @DisplayName("맞는 차량이 없으면 빈 목록이다")
    void emptyWhenNoMatch() {
        // when : 포르쉐는 픽스처에 없다
        List<AuctionListRow> rows = find(AuctionListGroup.LIVE, manufacturer(Manufacturer.PORSCHE), UNLIMITED);

        // then
        assertThat(rows).isEmpty();
    }

    // ================= 매핑 =================

    @Test
    @DisplayName("카드에 필요한 값이 세 테이블에서 모두 채워진다")
    void mapsAllCardFields() {
        // when
        AuctionListRow row = find(AuctionListGroup.LIVE, 1).getFirst();

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
    @DisplayName("나의 경매는 소유자 조건이 붙고 카드 값은 같은 자리에 채워진다")
    void myQuery_filtersBySellerAndMapsSameFields() {
        // given : 픽스처의 판매자는 100번이다

        // when
        AuctionListRow row = auctionListRepository.findPage(
                AuctionListGroup.LIVE, NO_FILTER, 100L,
                SNAPSHOT_AT, AuctionListGroup.LIVE.startSortAt(), AuctionListGroup.LIVE.startAuctionId(), 1)
                .getFirst();

        // then : 공개 목록 매핑 테스트와 같은 값이 나와야 한다
        assertThat(row.auctionId()).isEqualTo(101L);
        assertThat(row.vehicleId()).isEqualTo(901L);
        assertThat(row.manufacturer()).isEqualTo("HYUNDAI");
        assertThat(row.model()).isEqualTo("아반떼 CN7");
    }

    @Test
    @DisplayName("다른 판매자의 경매는 나의 목록에 나오지 않는다")
    void myQuery_excludesOtherSellers() {
        // when : 픽스처에 999번 판매자의 경매는 없다
        List<AuctionListRow> rows = auctionListRepository.findPage(
                AuctionListGroup.LIVE, NO_FILTER, 999L,
                SNAPSHOT_AT, AuctionListGroup.LIVE.startSortAt(), AuctionListGroup.LIVE.startAuctionId(), UNLIMITED);

        // then
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("나의 목록에도 차량 조건이 함께 걸린다")
    void myQuery_appliesVehicleFilter() {
        // when
        List<AuctionListRow> rows = auctionListRepository.findPage(
                AuctionListGroup.LIVE, manufacturer(Manufacturer.KIA), 100L,
                SNAPSHOT_AT, AuctionListGroup.LIVE.startSortAt(), AuctionListGroup.LIVE.startAuctionId(), UNLIMITED);

        // then
        assertThat(ids(rows)).containsExactly(102L, 103L);
    }

    @Test
    @DisplayName("입찰 전 경매는 현재가가 null 로 담긴다")
    void mapsNullCurrentPrice() {
        // given : 102번은 아직 입찰이 없다

        // when
        AuctionListRow row = find(AuctionListGroup.LIVE, 2).get(1);

        // then : 시작가로 대체하는 건 서비스의 몫이고 쿼리는 있는 그대로 담는다
        assertThat(row.auctionId()).isEqualTo(102L);
        assertThat(row.currentPrice()).isNull();
        assertThat(row.startPrice()).isEqualTo(38000000L);
    }

    // ================= 호출 =================
    // 그룹을 처음부터 읽을 때의 커서 시작값은 AuctionListGroup 이 안다

    private List<AuctionListRow> find(AuctionListGroup group, int limit) {
        return find(group, NO_FILTER, limit);
    }

    private List<AuctionListRow> find(AuctionListGroup group, AuctionListFilter filter, int limit) {
        return auctionListRepository.findPage(
                group, filter, null, SNAPSHOT_AT, group.startSortAt(), group.startAuctionId(), limit);
    }

    private AuctionListFilter manufacturer(Manufacturer manufacturer) {
        return new AuctionListFilter(manufacturer, null, null, null, null, null, null, null, null);
    }

    private List<Long> ids(List<AuctionListRow> rows) {
        return rows.stream().map(AuctionListRow::auctionId).toList();
    }
}
