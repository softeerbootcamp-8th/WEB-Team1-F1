package com.softeer.race.auctionlist.domain;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 목록에서 카드가 묶이는 단위이자 조회 순서
 * <p>
 * 그룹마다 정렬 기준이 다르다. 진행중은 마감이, 예정은 시작이 임박한 것부터고,
 * 종료는 이미 지난 시각이라 최근에 끝난 것부터 보여야 해서 방향이 뒤집힌다.
 * 한 order by 에 담으려면 정렬 키를 계산식으로 만들어야 하고 그러면 인덱스를 못 타므로,
 * 그룹마다 쿼리를 따로 둔다.
 */
public enum AuctionListGroup {

    LIVE(1, true),
    PENDING(2, true),
    ENDED(3, false);

    // 어떤 경매보다도 앞뒤인 값. MySQL DATETIME 범위(1000년~9999년) 안이어야 한다
    private static final LocalDateTime BEFORE_ANY = LocalDateTime.of(1000, 1, 1, 0, 0);
    private static final LocalDateTime AFTER_ANY = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    // 화면에 노출되는 순서. 선언 순서가 아니라 이 값이 기준이다
    private final int order;

    // 오름차순으로 읽는 그룹인가. 종료만 내림차순이다
    private final boolean ascending;

    AuctionListGroup(int order, boolean ascending) {
        this.order = order;
        this.ascending = ascending;
    }

    /**
     * 주어진 그룹부터 마지막 그룹까지, 노출 순서대로
     */
    public static List<AuctionListGroup> startingFrom(AuctionListGroup start) {
        return Arrays.stream(values())
                .filter(group -> group.order >= start.order)
                .sorted(Comparator.comparingInt(group -> group.order))
                .toList();
    }

    /**
     * 커서에 실려 돌아온 순번을 그룹으로 되돌린다
     */
    public static AuctionListGroup ofOrder(int order) {
        return Arrays.stream(values())
                .filter(group -> group.order == order)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 그룹 순번: " + order));
    }

    /**
     * 커서로 들어온 값이 그룹 순번으로 쓸 수 있는지. 요청 검증에서 쓴다
     */
    public static boolean isValidOrder(int order) {
        return Arrays.stream(values()).anyMatch(group -> group.order == order);
    }

    public int order() {
        return order;
    }

    /**
     * 그룹을 처음부터 읽을 때 쓰는 커서 시각. 읽는 방향에 따라 양 끝이 반대다
     */
    public LocalDateTime startSortAt() {
        return ascending ? BEFORE_ANY : AFTER_ANY;
    }

    /**
     * 그룹을 처음부터 읽을 때 쓰는 커서 식별자
     */
    public long startAuctionId() {
        return ascending ? 0L : Long.MAX_VALUE;
    }

    /**
     * 이 그룹에서 정렬 기준이 되는 시각. 커서에 담을 값이기도 하다
     */
    public LocalDateTime sortAtOf(AuctionListRow row) {
        return this == PENDING ? row.startTime() : row.currentEndTime();
    }
}
