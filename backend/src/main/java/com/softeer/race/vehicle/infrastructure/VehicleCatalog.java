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

    // 물리 컬럼명을 적어 둔다. 네이밍 전략이 알아서 바꿔주지만, 위 유니크 제약이 그 이름을 문자열로
    // 참조하고 있어 정적 분석이 둘을 연결하지 못한다. 이름을 여기 박아두면 오타를 IDE 가 잡는다.
    @Column(name = "plate_number", nullable = false)
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

    // 주행거리를 두지 않는다. 시점에 따라 변하는 값이라 원장이 들고 있으면 언제 측정한 값인지
    // 알 수 없고, 시세 계산에 그 낡은 값이 들어간다. 사용자가 요청마다 신고하는 값으로 옮겼다
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FuelType fuelType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Transmission transmission;

    /**
     * 이 모델의 기준가. 개별 차량의 연식·주행거리는 반영되지 않은 값이고, 예상 시세는 여기서
     * QuotePolicy 가 감가를 뺀 결과다. 이 값 자체는 응답에 나가지 않는다 — 노출되면 산정 로직이 역산된다.
     */
    @Column(nullable = false)
    private long basePrice;

    /** 대표 이미지. 없는 차량이 데모에 한 대 있어야 목록 카드의 이미지 없음 처리가 실제로 실행된다 */
    @Column
    private String mainImageUrl;

    /** 행은 DB에 시드되므로 프로덕션에서는 생성하지 않는다, 테스트에서 조립하기 위한 생성자다 */
    VehicleCatalog(String plateNumber, String ownerName, Manufacturer manufacturer, String model,
                   int modelYear, FuelType fuelType, Transmission transmission,
                   long basePrice, String mainImageUrl) {
        this.plateNumber = plateNumber;
        this.ownerName = ownerName;
        this.manufacturer = manufacturer;
        this.model = model;
        this.modelYear = modelYear;
        this.fuelType = fuelType;
        this.transmission = transmission;
        this.basePrice = basePrice;
        this.mainImageUrl = mainImageUrl;
    }

    /** 이 행의 제원을 포트가 내보낼 계약 타입으로 옮긴다 */
    // 변환은 보통 받는 쪽 정적 팩터리가 맡지만
    // 그러면 domain 의 VehicleSpec 이 infrastructure 를 알게 되어 의존 방향이 뒤집힌다. 그래서 주는 쪽에 둔다.
    VehicleSpec toSpec() {
        return new VehicleSpec(plateNumber, ownerName, manufacturer, model, modelYear,
                fuelType, transmission, basePrice, mainImageUrl);
    }
}
