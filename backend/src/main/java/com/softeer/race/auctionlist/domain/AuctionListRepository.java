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
 * 그룹마다 메서드를 따로 둔다. 한 쿼리로 묶으면 그룹 우선순위를 case 로 계산해야 하는데,
 * 그 값은 snapshotAt 에 따라 달라져 미리 정렬해둘 수 없으므로 어떤 인덱스도 못 탄다.
 * 나누면 각 쿼리의 정렬 키가 저장 컬럼 하나가 되어 필터와 정렬을 인덱스가 함께 해결한다.
 * <p>
 * 각 메서드에서 커서 비교 부호와 order by 방향이 짝을 이룬다. 어긋나면 커서가 가리키는
 * "다음"과 실제 순서가 달라져 조용히 깨진다.
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
     * <p>
     * 소프트클로즈로 마감이 연장되면 그 경매는 뒤로 밀린다. 실제로 덜 급해진 것이라 순서로는 맞지만,
     * 이미 보여준 경매가 커서를 넘어와 다시 나올 수 있다. 마감은 늘기만 하므로 누락은 생기지 않고,
     * 중복은 화면에서 auctionId 로 걸러낸다.
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
     * <p>
     * 시각이 과거라 오름차순으로 읽으면 오래전에 끝난 것이 먼저 나온다. 그래서 여기만 방향이 뒤집힌다.
     * 계산식이 없어 인덱스를 역방향으로 타고 요청한 개수만 읽으므로, 종료 경매를 무기한 보관해도
     * 이 쿼리의 비용은 늘지 않는다.
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
}
