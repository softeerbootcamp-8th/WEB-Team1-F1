package com.softeer.race.auctionroom.domain;

/**
 * 결과 화면에 보이는 조회자의 성적, 입찰한 적이 있어야 존재한다
 */
public record BidderStanding(
        long topBid,
        int rank
) {

    /**
     * 나보다 높이 부른 사람 수로 순위를 매긴다
     */
    // 입찰은 직전보다 높아야 성립하므로 최고가가 같은 두 사람이 나올 수 없다, 동점 규칙을 두지 않는다
    public static BidderStanding of(long topBid, long biddersAbove) {
        return new BidderStanding(topBid, (int) biddersAbove + 1);
    }
}
