package com.softeer.race.auctionlist.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 경매글 목록 조회. 그룹마다 정렬 키가 달라 쿼리를 나누고, 차량 조건은 값이 있는 것만 SQL 에 붙인다.
 */
@Repository
@RequiredArgsConstructor
public class AuctionListRepository {

    // 컬럼 순서는 CARD_ROW 가 위치로 매핑하므로 함께 고쳐야 한다.
    private static final String CARD_COLUMNS = """
                a.id, v.id, v.main_photo_url, v.manufacturer, v.model, v.model_year, v.mileage,
                a.start_price, a.current_price, a.room_open_at, a.start_time, a.current_end_time
            from auction a
            join auction_post p on p.id = a.post_id
            join vehicle v on v.id = p.vehicle_id
            where p.deleted_at is null
            """;

    // 옵티마이저가 limit 조기 종료를 비용에 못 넣어 조인 순서를 경매부터로 고정한다(10만 건 실측 11ms vs 254ms).
    private static final String SELECT_HINTED = "select /*+ JOIN_ORDER(a, p, v) */" + CARD_COLUMNS;

    // 나의 목록은 소유 건수가 적을수록 판매자부터 출발하는 편이 빨라 힌트를 걸지 않는다.
    private static final String SELECT_PLAIN = "select" + CARD_COLUMNS;

    // 진행중, 마감이 임박한 것부터
    private static final String LIVE_CONDITION = """
            and a.start_time <= :snapshotAt and :snapshotAt < a.current_end_time
            and (a.current_end_time > :cursorSortAt
                 or (a.current_end_time = :cursorSortAt and a.id > :cursorAuctionId))
            """;
    private static final String LIVE_ORDER = "order by a.current_end_time, a.id\n";

    // 예정, 시작이 임박한 것부터
    private static final String PENDING_CONDITION = """
            and :snapshotAt < a.start_time
            and (a.start_time > :cursorSortAt
                 or (a.start_time = :cursorSortAt and a.id > :cursorAuctionId))
            """;
    private static final String PENDING_ORDER = "order by a.start_time, a.id\n";

    // 종료, 최근에 끝난 것부터
    private static final String ENDED_CONDITION = """
            and a.current_end_time <= :snapshotAt
            and (a.current_end_time < :cursorSortAt
                 or (a.current_end_time = :cursorSortAt and a.id < :cursorAuctionId))
            """;
    private static final String ENDED_ORDER = "order by a.current_end_time desc, a.id desc\n";

    // 가격과 주행거리는 null 이 올 수 있어 getObject 로 읽는다.
    private static final RowMapper<AuctionListRow> CARD_ROW = (rs, rowNum) -> new AuctionListRow(
            rs.getLong(1),
            rs.getLong(2),
            rs.getString(3),
            rs.getString(4),
            rs.getString(5),
            rs.getObject(6, Integer.class),
            rs.getObject(7, Integer.class),
            rs.getObject(8, Long.class),
            rs.getObject(9, Long.class),
            rs.getObject(10, LocalDateTime.class),
            rs.getObject(11, LocalDateTime.class),
            rs.getObject(12, LocalDateTime.class));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<AuctionListRow> findPage(AuctionListGroup group, AuctionListFilter filter, Long sellerId,
                                         LocalDateTime snapshotAt, LocalDateTime cursorSortAt,
                                         long cursorAuctionId, int limit) {
        // null 인 조건은 없는 것으로 친다.
        AuctionListFilter vehicleFilter = (filter != null) ? filter : AuctionListFilter.none();

        StringBuilder sql = new StringBuilder(sellerId != null ? SELECT_PLAIN : SELECT_HINTED);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotAt", snapshotAt)
                .addValue("cursorSortAt", cursorSortAt)
                .addValue("cursorAuctionId", cursorAuctionId)
                .addValue("limit", limit);

        sql.append(condition(group));

        if (sellerId != null) {
            sql.append("and v.seller_id = :sellerId\n");
            params.addValue("sellerId", sellerId);
        }

        appendFilter(sql, params, vehicleFilter);

        sql.append(order(group)).append("limit :limit");

        return jdbcTemplate.query(sql.toString(), params, CARD_ROW);
    }

    /**
     * 방송할 경매 하나. 삭제된 경매글은 목록과 같은 이유로 빠진다.
     */
    public Optional<AuctionListRow> findRow(long auctionId) {
        // 방송이 들고 오는 것은 경매 id 하나뿐이라 커서로 페이지를 읽는 쿼리로는 지목할 수 없다.
        List<AuctionListRow> rows = jdbcTemplate.query(
                SELECT_PLAIN + "and a.id = :auctionId",
                new MapSqlParameterSource("auctionId", auctionId),
                CARD_ROW);
        return rows.stream().findFirst();
    }

    private void appendFilter(StringBuilder sql, MapSqlParameterSource params, AuctionListFilter filter) {
        if (filter.manufacturer() != null) {
            sql.append("and v.manufacturer = :manufacturer\n");
            params.addValue("manufacturer", filter.manufacturer().name());
        }
        if (filter.fuelTypes() != null && !filter.fuelTypes().isEmpty()) {
            sql.append("and v.fuel_type in (:fuelTypes)\n");
            params.addValue("fuelTypes", filter.fuelTypes().stream().map(Enum::name).toList());
        }
        if (filter.transmission() != null) {
            sql.append("and v.transmission = :transmission\n");
            params.addValue("transmission", filter.transmission().name());
        }
        if (filter.mileageMin() != null) {
            sql.append("and v.mileage >= :mileageMin\n");
            params.addValue("mileageMin", filter.mileageMin());
        }
        if (filter.mileageMax() != null) {
            sql.append("and v.mileage <= :mileageMax\n");
            params.addValue("mileageMax", filter.mileageMax());
        }
        if (filter.modelYearMin() != null) {
            sql.append("and v.model_year >= :modelYearMin\n");
            params.addValue("modelYearMin", filter.modelYearMin());
        }
        if (filter.modelYearMax() != null) {
            sql.append("and v.model_year <= :modelYearMax\n");
            params.addValue("modelYearMax", filter.modelYearMax());
        }
        // 가격은 화면 표시 규칙(현재가, 없으면 시작가)과 같은 값으로 거른다.
        if (filter.priceMin() != null) {
            sql.append("and coalesce(a.current_price, a.start_price) >= :priceMin\n");
            params.addValue("priceMin", filter.priceMin());
        }
        if (filter.priceMax() != null) {
            sql.append("and coalesce(a.current_price, a.start_price) <= :priceMax\n");
            params.addValue("priceMax", filter.priceMax());
        }
    }

    private String condition(AuctionListGroup group) {
        return switch (group) {
            case LIVE -> LIVE_CONDITION;
            case PENDING -> PENDING_CONDITION;
            case ENDED -> ENDED_CONDITION;
        };
    }

    private String order(AuctionListGroup group) {
        return switch (group) {
            case LIVE -> LIVE_ORDER;
            case PENDING -> PENDING_ORDER;
            case ENDED -> ENDED_ORDER;
        };
    }
}
