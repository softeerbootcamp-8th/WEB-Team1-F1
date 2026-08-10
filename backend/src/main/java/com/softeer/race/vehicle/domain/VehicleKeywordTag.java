package com.softeer.race.vehicle.domain;

import com.softeer.race.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차량에 매겨진 키워드 한 개.
 * <p>
 * {@code @ElementCollection}으로 두지 않는다. 그러면 차량을 거쳐야만 접근할 수 있어, 목록처럼
 * 여러 차량의 키워드가 필요한 화면에서 건수만큼 조회가 나간다. 별도 엔티티면 저장소에
 * {@code vehicle_id in (...)}을 더해 페이지 전체를 한 번에 읽을 수 있다 — {@link VehicleImage}가
 * 같은 이유로 엔티티다.
 * <p>
 * 정렬 컬럼을 두지 않는다. 표시 순서는 {@link VehicleKeyword}의 선언 순서다.
 */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"vehicle_id", "keyword"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleKeywordTag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleKeyword keyword;

    private VehicleKeywordTag(Vehicle vehicle, VehicleKeyword keyword) {
        this.vehicle = vehicle;
        this.keyword = keyword;
    }

    public static VehicleKeywordTag create(Vehicle vehicle, VehicleKeyword keyword) {
        return new VehicleKeywordTag(vehicle, keyword);
    }
}
