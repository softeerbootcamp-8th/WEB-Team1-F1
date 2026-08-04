package com.softeer.race.auctionlist.domain;

import com.softeer.race.auction.domain.Auction;
import com.softeer.race.auctionpost.domain.PostStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 경매글 목록 화면에 필요한 경매 조회
 * <p>
 * 그룹마다 쿼리를 나눠야 정렬 키가 저장 컬럼 하나가 되어 인덱스가 필터와 정렬을 함께 해결한다.
 */
public interface AuctionListRepository extends Repository<Auction, Long> {

    String SELECT_CARD = """
            select new com.softeer.race.auctionlist.domain.AuctionListRow(
                a.id, p.thumbnailUrl, v.model, v.modelYear, v.mileage,
                a.startPrice, a.currentPrice, a.roomOpenAt, a.startTime, a.currentEndTime)
            from Auction a
            join a.post p
            join p.vehicle v
            where p.postStatus = :published and p.deletedAt is null
            """;

    /**
     * 진행중, 마감이 임박한 것부터
     */
    @Query(SELECT_CARD + """
            and a.startTime <= :snapshotAt and :snapshotAt < a.currentEndTime
            and (a.currentEndTime > :cursorSortAt
                 or (a.currentEndTime = :cursorSortAt and a.id > :cursorAuctionId))
            order by a.currentEndTime, a.id
            """)
    List<AuctionListRow> findLivePage(@Param("published") PostStatus published,
                                      @Param("snapshotAt") LocalDateTime snapshotAt,
                                      @Param("cursorSortAt") LocalDateTime cursorSortAt,
                                      @Param("cursorAuctionId") long cursorAuctionId,
                                      Limit limit);

    /**
     * 예정, 시작이 임박한 것부터
     * <p>
     * 아직 시작 전이라 입찰이 없고, 따라서 마감이 연장될 일도 없다.
     */
    @Query(SELECT_CARD + """
            and :snapshotAt < a.startTime
            and (a.startTime > :cursorSortAt
                 or (a.startTime = :cursorSortAt and a.id > :cursorAuctionId))
            order by a.startTime, a.id
            """)
    List<AuctionListRow> findPendingPage(@Param("published") PostStatus published,
                                         @Param("snapshotAt") LocalDateTime snapshotAt,
                                         @Param("cursorSortAt") LocalDateTime cursorSortAt,
                                         @Param("cursorAuctionId") long cursorAuctionId,
                                         Limit limit);

    /**
     * 종료, 최근에 끝난 것부터
     */
    @Query(SELECT_CARD + """
            and a.currentEndTime <= :snapshotAt
            and (a.currentEndTime < :cursorSortAt
                 or (a.currentEndTime = :cursorSortAt and a.id < :cursorAuctionId))
            order by a.currentEndTime desc, a.id desc
            """)
    List<AuctionListRow> findEndedPage(@Param("published") PostStatus published,
                                       @Param("snapshotAt") LocalDateTime snapshotAt,
                                       @Param("cursorSortAt") LocalDateTime cursorSortAt,
                                       @Param("cursorAuctionId") long cursorAuctionId,
                                       Limit limit);

    /**
     * 나의 진행중, 등치 조건이라야 옵티마이저가 vehicle 인덱스에서 출발할 수 있다.
     */
    @Query(SELECT_CARD + """
            and v.seller.id = :sellerId
            and a.startTime <= :snapshotAt and :snapshotAt < a.currentEndTime
            and (a.currentEndTime > :cursorSortAt
                 or (a.currentEndTime = :cursorSortAt and a.id > :cursorAuctionId))
            order by a.currentEndTime, a.id
            """)
    List<AuctionListRow> findMyLivePage(@Param("published") PostStatus published,
                                        @Param("sellerId") long sellerId,
                                        @Param("snapshotAt") LocalDateTime snapshotAt,
                                        @Param("cursorSortAt") LocalDateTime cursorSortAt,
                                        @Param("cursorAuctionId") long cursorAuctionId,
                                        Limit limit);

    /**
     * 나의 예정
     */
    @Query(SELECT_CARD + """
            and v.seller.id = :sellerId
            and :snapshotAt < a.startTime
            and (a.startTime > :cursorSortAt
                 or (a.startTime = :cursorSortAt and a.id > :cursorAuctionId))
            order by a.startTime, a.id
            """)
    List<AuctionListRow> findMyPendingPage(@Param("published") PostStatus published,
                                           @Param("sellerId") long sellerId,
                                           @Param("snapshotAt") LocalDateTime snapshotAt,
                                           @Param("cursorSortAt") LocalDateTime cursorSortAt,
                                           @Param("cursorAuctionId") long cursorAuctionId,
                                           Limit limit);

    /**
     * 나의 종료
     */
    @Query(SELECT_CARD + """
            and v.seller.id = :sellerId
            and a.currentEndTime <= :snapshotAt
            and (a.currentEndTime < :cursorSortAt
                 or (a.currentEndTime = :cursorSortAt and a.id < :cursorAuctionId))
            order by a.currentEndTime desc, a.id desc
            """)
    List<AuctionListRow> findMyEndedPage(@Param("published") PostStatus published,
                                         @Param("sellerId") long sellerId,
                                         @Param("snapshotAt") LocalDateTime snapshotAt,
                                         @Param("cursorSortAt") LocalDateTime cursorSortAt,
                                         @Param("cursorAuctionId") long cursorAuctionId,
                                         Limit limit);
}
