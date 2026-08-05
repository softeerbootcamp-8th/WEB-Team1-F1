package com.softeer.race.progress.domain;

import com.softeer.race.vehicle.domain.Vehicle;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 판매자 진행 상황 조회
 * <p>
 * 차량에서 출발한다. 평가와 경매 어느 쪽에서 출발해도 반대쪽 경로로 들어온 신청이 목록에서
 * 통째로 빠지기 때문이다 — 판매 신청에는 평가가 없고 방문견적에는 경매가 없다. 두 갈래가
 * 공통으로 매달려 있는 것은 차량뿐이다.
 * <p>
 * 매핑된 역방향 컬렉션이 없어(차량은 자기 평가나 경매글을 모른다) 엔티티 조인으로 붙인다.
 * 연관을 새로 매핑하지 않는 이유는 그 컬렉션이 이 조회 하나를 위한 것이고, 쓰기 쪽 엔티티에
 * 조회 전용 연관이 생기면 영속성 전이와 고아 제거를 함께 고민해야 하기 때문이다.
 */
public interface SellerProgressRepository extends Repository<Vehicle, Long> {

    String SELECT_ROW = """
            select new com.softeer.race.progress.domain.SellerProgressRow(
                v.id, v.manufacturer, v.model, v.modelYear, v.mileage, v.plateNumber,
                v.estimatedPrice, v.createdAt,
                e.status, ev.id, e.visitDate, e.rejectReason,
                p.thumbnailUrl, p.deletedAt,
                a.id, a.status, a.startPrice, a.currentPrice, a.startTime, a.currentEndTime)
            from Vehicle v
            left join Evaluation e on e.vehicle = v
            left join e.evaluator ev
            left join AuctionPost p on p.vehicle = v
            left join Auction a on a.post = p
            where v.seller.id = :sellerId
            """;

    /**
     * 평가도 경매글도 없는 차량은 뺀다. 보여줄 진행이 없을뿐더러, 남겨 두면 단계를 정할 수 없는
     * 행이 그대로 흘러가 {@link ProgressStage#of}가 터진다.
     * <p>
     * 삭제된 경매글은 조인 조건으로 걸러내지 않는다. 걸러내면 판매자가 내린 글이 평가 단계로
     * 되돌아간 것처럼 보이거나, 평가가 없는 판매 신청 건은 목록에서 사라진다.
     */
    @Query(SELECT_ROW + """
            and (e.id is not null or p.id is not null)
            order by v.id desc
            """)
    List<SellerProgressRow> findAllBySeller(@Param("sellerId") long sellerId);

    /**
     * 남의 차량은 조건에 걸려 빈 값이 된다. 소유자가 아님을 403으로 알리지 않는 것은 그 응답이
     * "그 번호의 차량은 있다"를 알려주기 때문이다.
     */
    @Query(SELECT_ROW + """
            and v.id = :vehicleId
            and (e.id is not null or p.id is not null)
            """)
    Optional<SellerProgressRow> findOneBySeller(@Param("sellerId") long sellerId,
                                                @Param("vehicleId") long vehicleId);
}
