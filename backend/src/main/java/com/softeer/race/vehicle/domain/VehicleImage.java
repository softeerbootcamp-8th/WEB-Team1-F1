package com.softeer.race.vehicle.domain;

import com.softeer.race.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
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
     */
    public static VehicleImage create(Vehicle vehicle, String imageUrl, int sortOrder) {
        return new VehicleImage(vehicle, imageUrl, sortOrder);
    }
}
