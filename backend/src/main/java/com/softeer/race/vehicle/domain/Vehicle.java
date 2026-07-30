package com.softeer.race.vehicle.domain;

import com.softeer.race.common.domain.BaseTimeEntity;
import com.softeer.race.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class Vehicle extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Manufacturer manufacturer;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private int modelYear;

    @Column(nullable = false)
    private int mileage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FuelType fuelType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Transmission transmission;

    @Column(nullable = false)
    private String plateNumber;

    private Long estimatedPrice;

    private Vehicle(User seller, VehicleSpec spec) {
        this.seller = seller;
        this.manufacturer = spec.manufacturer();
        this.model = spec.model();
        this.modelYear = spec.modelYear();
        this.mileage = spec.mileage();
        this.fuelType = spec.fuelType();
        this.transmission = spec.transmission();
        this.plateNumber = spec.plateNumber();
        this.estimatedPrice = spec.basePrice();
    }

    /**
     * 조회된 제원으로 판매자의 차량을 만든다.
     * <p>
     * 제원을 개별 파라미터로 늘어놓지 않고 {@link VehicleSpec}을 통째로 받는 이유가 둘이다.
     * 첫째, 클라이언트가 보낸 값으로 차량을 만드는 경로가 타입 수준에서 사라져 "제원은 서버가 재조회해
     * 채운다"가 컴파일 타임에 강제된다. 둘째, modelYear와 mileage가 둘 다 int라 9-인자 팩토리에서는
     * 2021과 45000이 서로 뒤바뀌어도 컴파일과 테스트를 모두 통과한다. record를 거치면 그 실패가 없다.
     * <p>
     * 판매자가 제원을 직접 입력하는 요구가 생기면 그때 오버로드를 추가한다.
     */
    public static Vehicle create(User seller, VehicleSpec spec) {
        return new Vehicle(seller, spec);
    }
}
