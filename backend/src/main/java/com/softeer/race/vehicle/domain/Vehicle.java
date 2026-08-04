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

    private Vehicle(User seller, VehicleSpec spec, int mileage, long estimatedPrice) {
        this.seller = seller;
        this.manufacturer = spec.manufacturer();
        this.model = spec.model();
        this.modelYear = spec.modelYear();
        this.mileage = mileage;
        this.fuelType = spec.fuelType();
        this.transmission = spec.transmission();
        this.plateNumber = spec.plateNumber();
        this.estimatedPrice = estimatedPrice;
    }

    /**
     * 조회된 제원과 신고된 주행거리로 판매자의 차량을 만든다.
     * <p>
     * 제원을 개별 파라미터로 늘어놓지 않고 {@link VehicleSpec}을 통째로 받는다. 클라이언트가 보낸
     * 값으로 제조사·모델·연식·연료·변속기를 채우는 경로가 타입 수준에서 사라져 "그 제원은 서버가
     * 재조회해 채운다"가 컴파일 타임에 강제된다.
     * <p>
     * <b>주행거리만 예외다.</b> 번호판에 고정된 사실이 아니라 시점에 따라 변하는 값이라 조회기가
     * 들고 있을 수 없어({@link VehicleSpec} 주석 참고) 사용자 신고값을 받는다. 그래서 이 인자는
     * 위조 가능하고, 낮게 신고하면 예상 시세와 경매 시작가가 함께 부풀려진다. 방문견적 흐름은
     * 평가사가 방문해 실측하므로 그 지점에서 교정되지만, 평가를 거치지 않는 경로에는 검증이 없다.
     * <p>
     * spec에 modelYear가 남아 있어 두 int가 뒤바뀔 위험은 없다. mileage는 int이고 estimatedPrice는
     * long이라 순서를 바꿔 넘기면 컴파일되지 않는다.
     * <p>
     * 예상 시세도 spec에서 꺼내지 않고 따로 받는다. {@link VehicleSpec#basePrice()}는 그 모델의
     * 기준가라 그대로 넣으면 신차급 가격이 예상 시세로 저장돼 목록·경매방 응답에 실려 나간다.
     * 감가를 반영하는 것은 정책의 일이라 호출자가 계산해 넘긴다.
     */
    public static Vehicle create(User seller, VehicleSpec spec, int mileage, long estimatedPrice) {
        return new Vehicle(seller, spec, mileage, estimatedPrice);
    }
}
