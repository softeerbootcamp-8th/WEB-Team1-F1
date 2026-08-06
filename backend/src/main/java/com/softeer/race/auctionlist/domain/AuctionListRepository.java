package com.softeer.race.auctionlist.domain;

import com.softeer.race.auction.domain.Auction;
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
 * 공개 목록만 조인 순서 힌트를 걸어야 해서 네이티브고, 나의 경매는 JPQL 그대로다.
 */
public interface AuctionListRepository extends Repository<Auction, Long> {

    /**
     * 조인 순서를 경매부터로 고정한다. 옵티마이저가 조인 순서를 고를 때 limit 을 비용에 넣지 않아,
     * 정렬 인덱스로 필요한 만큼만 읽고 끝낼 수 있다는 이 계획의 장점을 보지 못하기 때문이다.
     * <p>
     * 힌트가 별칭으로 테이블을 지목하고 결과는 아래 순서대로 레코드에 들어가므로,
     * 별칭과 컬럼 순서는 AuctionListRow 와 함께 고쳐야 한다.
     */
    String SELECT_CARD_SQL = """
            select /*+ JOIN_ORDER(a, p, v) */
                a.id, v.main_photo_url, v.model, v.model_year, v.mileage,
                a.start_price, a.current_price, a.room_open_at, a.start_time, a.current_end_time
            from auction a
            join auction_post p on p.id = a.post_id
            join vehicle v on v.id = p.vehicle_id
            where p.deleted_at is null
            """;

    String SELECT_CARD = """
            select new com.softeer.race.auctionlist.domain.AuctionListRow(
                a.id, v.mainPhotoUrl, v.model, v.modelYear, v.mileage,
                a.startPrice, a.currentPrice, a.roomOpenAt, a.startTime, a.currentEndTime)
            from Auction a
            join a.post p
            join p.vehicle v
            where p.deletedAt is null
            """;

    /**
     * 진행중, 마감이 임박한 것부터
     */
    @Query(value = SELECT_CARD_SQL + """
            and a.start_time <= :snapshotAt and :snapshotAt < a.current_end_time
            and (a.current_end_time > :cursorSortAt
                 or (a.current_end_time = :cursorSortAt and a.id > :cursorAuctionId))
            order by a.current_end_time, a.id
            limit :limit
            """, nativeQuery = true)
    List<AuctionListRow> findLivePage(@Param("snapshotAt") LocalDateTime snapshotAt,
                                      @Param("cursorSortAt") LocalDateTime cursorSortAt,
                                      @Param("cursorAuctionId") long cursorAuctionId,
                                      @Param("limit") int limit);

    /**
     * 예정, 시작이 임박한 것부터. 아직 입찰이 없어 마감이 연장될 일도 없다.
     */
    @Query(value = SELECT_CARD_SQL + """
            and :snapshotAt < a.start_time
            and (a.start_time > :cursorSortAt
                 or (a.start_time = :cursorSortAt and a.id > :cursorAuctionId))
            order by a.start_time, a.id
            limit :limit
            """, nativeQuery = true)
    List<AuctionListRow> findPendingPage(@Param("snapshotAt") LocalDateTime snapshotAt,
                                         @Param("cursorSortAt") LocalDateTime cursorSortAt,
                                         @Param("cursorAuctionId") long cursorAuctionId,
                                         @Param("limit") int limit);

    /**
     * 종료, 최근에 끝난 것부터
     */
    @Query(value = SELECT_CARD_SQL + """
            and a.current_end_time <= :snapshotAt
            and (a.current_end_time < :cursorSortAt
                 or (a.current_end_time = :cursorSortAt and a.id < :cursorAuctionId))
            order by a.current_end_time desc, a.id desc
            limit :limit
            """, nativeQuery = true)
    List<AuctionListRow> findEndedPage(@Param("snapshotAt") LocalDateTime snapshotAt,
                                       @Param("cursorSortAt") LocalDateTime cursorSortAt,
                                       @Param("cursorAuctionId") long cursorAuctionId,
                                       @Param("limit") int limit);

    /**
     * 나의 진행중. 소유 건수가 적을수록 판매자부터 출발하는 편이 빨라 힌트를 걸지 않는다.
     */
    @Query(SELECT_CARD + """
            and v.seller.id = :sellerId
            and a.startTime <= :snapshotAt and :snapshotAt < a.currentEndTime
            and (a.currentEndTime > :cursorSortAt
                 or (a.currentEndTime = :cursorSortAt and a.id > :cursorAuctionId))
            order by a.currentEndTime, a.id
            """)
    List<AuctionListRow> findMyLivePage(@Param("sellerId") long sellerId,
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
    List<AuctionListRow> findMyPendingPage(@Param("sellerId") long sellerId,
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
    List<AuctionListRow> findMyEndedPage(@Param("sellerId") long sellerId,
                                         @Param("snapshotAt") LocalDateTime snapshotAt,
                                         @Param("cursorSortAt") LocalDateTime cursorSortAt,
                                         @Param("cursorAuctionId") long cursorAuctionId,
                                         Limit limit);
}
