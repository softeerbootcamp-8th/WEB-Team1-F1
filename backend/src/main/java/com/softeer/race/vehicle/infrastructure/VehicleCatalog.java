package com.softeer.race.vehicle.infrastructure;

import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import com.softeer.race.vehicle.domain.VehicleSpec;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차량 제원 원장(vehicle_catalog) 테이블과 매핑되는 엔티티. 인스턴스 하나가 실제 차 한 대다.
 *
 * <p>우리 서비스에 등록된 차(Vehicle)가 아니라 세상에 존재하는 차를 뜻한다.
 * 외부 차량 정보 서비스를 대신하는 대역이라 domain 이 아니라 infrastructure 에 두었고,
 * 외부 연동으로 전환하면 이 패키지째 사라진다.
 */
@Getter
@Entity
// 번호판은 실제 세계에서 유일하므로 유일하다.
// 판매 신청이 만드는 vehicle.plate_number 가 유일하지 않은 것과는 다른 이야기다 —
// 같은 차가 시간을 두고 다시 매물로 나올 수 있어 그쪽은 중복을 허용한다.
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_vehicle_catalog_plate_number", columnNames = "plate_number"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String plateNumber;

    @Column(nullable = false)
    private String ownerName;

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

    /** 시세 산정의 입력이 되는 참고가, 이 값 자체는 응답에 나가지 않는다 */
    @Column(nullable = false)
    private long basePrice;

    @Column(nullable = false)
    private String imageUrl;

    /** 행은 DB에 시드되므로 프로덕션에서는 생성하지 않는다, 테스트에서 조립하기 위한 생성자다 */
    VehicleCatalog(String plateNumber, String ownerName, Manufacturer manufacturer, String model,
                   int modelYear, int mileage, FuelType fuelType, Transmission transmission,
                   long basePrice, String imageUrl) {
        this.plateNumber = plateNumber;
        this.ownerName = ownerName;
        this.manufacturer = manufacturer;
        this.model = model;
        this.modelYear = modelYear;
        this.mileage = mileage;
        this.fuelType = fuelType;
        this.transmission = transmission;
        this.basePrice = basePrice;
        this.imageUrl = imageUrl;
    }

    /** 이 행의 제원을 차량 조회기가 내보낼 계약 타입으로 옮긴다 */
    // 변환은 보통 받는 쪽 정적 팩터리가 맡지만
    // 그러면 domain 의 VehicleSpec 이 infrastructure 를 알게 되어 의존 방향이 뒤집힌다. 그래서 주는 쪽에 둔다.
    VehicleSpec toSpec() {
        return new VehicleSpec(manufacturer, model, modelYear, mileage, fuelType,
                transmission, ownerName, basePrice, imageUrl);
    }
}