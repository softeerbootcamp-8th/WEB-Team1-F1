package com.softeer.race.vehicle.domain;

import com.softeer.race.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(nullable = false)
    private String imageUrl;

    @Column(nullable = false)
    private int sortOrder;

    private VehicleImage(Vehicle vehicle, String imageUrl, int sortOrder) {
        this.vehicle = vehicle;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    /**
     * 차량 이미지를 만든다.
     * <p>
     * isThumbnail 같은 대표 이미지 플래그를 두지 않는다. 대표 이미지 규칙은 이미
     * {@link VehicleImageRepository#findFirstByVehicleOrderBySortOrderAsc}에 "sortOrder 최솟값"으로
     * 한 번 정의돼 있고, 플래그를 더하면 두 규칙이 서로 어긋날 수 있다.
     */
    public static VehicleImage create(Vehicle vehicle, String imageUrl, int sortOrder) {
        return new VehicleImage(vehicle, imageUrl, sortOrder);
    }
}
