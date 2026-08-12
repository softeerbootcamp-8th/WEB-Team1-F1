package com.softeer.race.vehicle.domain;

import com.softeer.race.common.domain.BaseTimeEntity;
import com.softeer.race.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 출품 단위의 차량, 행이 평가 신청마다 새로 생겨 같은 실물 차가 여러 행일 수 있고 한 행에 평가는 하나다
 */
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

    /**
     * 진단 전에는 비어 있다. 방문견적 신청은 "이 차를 봐 주세요"라는 예약이라 주행거리를 알 수 없고,
     * 실측은 평가사가 방문해서 한다. 신청자에게 물어 받아 두면 검증되지 않은 숫자가 차량에 남는다.
     * <p>
     * <b>경매가 붙은 차량은 항상 채워져 있다</b>는 불변식이 있다. 판매 신청은 주행거리를 받아 곧바로
     * 경매를 만들고, 방문견적으로 만들어진 차량은 진단을 거쳐 값이 채워진 뒤에야 출품된다.
     * 경매 목록과 경매방이 이 값을 원시 int 로 받는 것이 그 불변식에 기대고 있다.
     */
    private Integer mileage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FuelType fuelType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Transmission transmission;

    @Column(nullable = false)
    private String plateNumber;

    private Long estimatedPrice;

    // 평가사가 결과를 낼 때 고르는 값이라 진단 전에는 비어 있다
    private String mainPhotoUrl;

    // 평가사가 결과와 함께 올리는 값이라 진단 전에는 비어 있다
    private String diagnosticReportUrl;

    private Vehicle(User seller, VehicleSpec spec, Integer mileage, Long estimatedPrice) {
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
     * 위조 가능하고, 낮게 신고하면 예상 시세와 경매 시작가가 함께 부풀려진다. 이 팩토리를 쓰는
     * 판매 신청은 평가를 거치지 않고 곧바로 경매가 되어 그 값을 검증할 단계가 없다.
     * 평가사 실측을 거치는 경로는 {@link #pendingDiagnosis}를 쓴다.
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

    /**
     * 평가사 진단을 기다리는 차량을 만든다. 주행거리와 예상 시세는 비어 있다.
     * <p>
     * 방문견적 신청이 쓰는 경로다. 그 시점에 아는 것은 "이 번호판의 차를 봐 달라"는 것뿐이고,
     * 주행거리는 평가사가 실측하고 시세는 평가사가 산정한다. 신청자에게 물어 받아 두면 검증되지 않은
     * 값이 차량에 남고, {@link com.softeer.race.quote.domain.QuotePolicy}로 미리 계산해 두면
     * 아무것도 보증하지 않는 금액이 응답에 실려 나간다.
     * <p>
     * {@link #create}와 갈라 두는 이유는 두 경로가 채울 수 있는 값이 다르다는 것을 타입으로
     * 드러내기 위해서다. 인자에 null 을 넘겨 하나로 합치면 호출자가 무엇을 비워도 되는지 알 수 없다.
     */
    public static Vehicle pendingDiagnosis(User seller, VehicleSpec spec) {
        return new Vehicle(seller, spec, null, null);
    }

    /** 이 차량의 판매자인지 식별자만으로 확인한다. */
    public boolean isOwnedBy(long userId) {
        return seller.getId().equals(userId);
    }

    /**
     * 평가사가 실측·산정하고 고른 값으로 비어 있던 네 칸을 채운다. {@link #pendingDiagnosis}가 만든
     * 차량이 온전해지는 지점이다.
     * <p>
     * <b>"경매가 붙은 차량은 mileage가 채워져 있다"는 불변식이 여기에 달려 있다.</b> 방문견적으로
     * 만들어진 차량은 이 메서드를 거쳐야만 값을 갖고, 출품은 그 뒤에 일어난다. 조각난 API로 나눠
     * 채우게 두면 주행거리나 대표 사진이나 진단서가 빈 차가 경매에 올라가 목록과 경매방이 깨진다.
     * <p>
     * 이미 채워진 값을 덮어쓸 수 있다. 평가사가 잘못 적은 주행거리를 고치려면 결과를 다시
     * 제출해야 하고, 그 재제출이 여기로 온다.
     * <p>
     * 시세를 {@code QuotePolicy}로 다시 계산하지 않는다. 실물을 보고 사람이 매긴 값이 그 계산보다
     * 나은 근거를 갖는 것이 방문견적의 존재 이유다. 만원 단위로 내리지도 않는다 — 그 내림은
     * 근거 없는 정밀도를 감추려는 장치이고, 사람이 부른 금액에는 그 문제가 없다.
     */
    public void completeDiagnosis(int mileage, long estimatedPrice,
                                  String mainPhotoUrl, String diagnosticReportUrl) {
        this.mileage = mileage;
        this.estimatedPrice = estimatedPrice;
        this.mainPhotoUrl = mainPhotoUrl;
        this.diagnosticReportUrl = diagnosticReportUrl;
    }

    /**
     * 실측 주행거리만 고쳐 적는다.
     * <p>
     * {@link #completeDiagnosis}와 나눠 두는 이유는 <b>부르는 시점이 다르기</b> 때문이다. 저쪽은
     * 비어 있던 네 칸을 한꺼번에 채워 차량을 온전하게 만드는 지점이라 넷을 전부 요구해야 하지만,
     * 이 메서드는 이미 온전한 차량의 한 칸만 바꾼다. 나머지 셋을 다시 받게 하면 사진 한 장을
     * 바꾸려는 평가사가 주행거리와 시세를 함께 보내야 하는 지금 문제가 그대로 남는다.
     * <p>
     * <b>진단 전 차량에 불러서는 안 된다.</b> mileage만 채워지고 estimatedPrice가 비어 있는 차량이
     * 생겨 {@link #isDiagnosed}가 거짓인 채로 값이 남는다. 그 관문은 호출자가 지킨다 —
     * {@code Evaluation.approve}가 바로 앞의 검증을 전제로 두는 것과 같다.
     */
    public void reviseMileage(int mileage) {
        this.mileage = mileage;
    }

    /**
     * 산정한 예상 시세만 고쳐 적는다. {@link #reviseMileage}와 같은 이유로 나눠 둔다.
     * <p>
     * 여기서도 {@code QuotePolicy}로 다시 계산하지 않고 만원 단위로 내리지도 않는다.
     * 실물을 보고 사람이 매긴 값이라는 점이 최초 제출과 다르지 않다.
     */
    public void reviseEstimatedPrice(long estimatedPrice) {
        this.estimatedPrice = estimatedPrice;
    }

    /**
     * 진단서만 새 주소로 갈아 끼운다.
     * <p>
     * 이전 주소의 파일을 지우지 않는다. 저장소에서 지우는 일은 실패해도 롤백되지 않아 트랜잭션
     * 밖의 부작용이 되고, 지워진 뒤 이 트랜잭션이 롤백되면 차량이 없는 파일을 가리키게 된다.
     */
    public void replaceDiagnosticReport(String diagnosticReportUrl) {
        this.diagnosticReportUrl = diagnosticReportUrl;
    }

    /**
     * 대표 사진을 바꾼다. 사진 목록이 바뀔 때마다 <b>반드시 함께</b> 불러야 한다.
     * <p>
     * 대표 사진이 vehicle 행에 따로 있고 갤러리는 vehicle_image에 있어, 목록만 갈아 끼우면
     * 방금 지운 사진의 주소가 대표로 남는다. 경매 목록 썸네일과 경매방이 그 값을 읽으므로
     * 목록에서 사라진 사진이 카드에는 계속 보이게 된다.
     * <p>
     * 목록의 첫 장을 넣는 것은 호출자의 몫이다. "첫 장이 대표"라는 규칙은 사진 순서를 정하는
     * 쪽이 들고 있고, 차량은 어느 것이 첫 장인지 알지 못한다.
     */
    public void changeMainPhoto(String mainPhotoUrl) {
        this.mainPhotoUrl = mainPhotoUrl;
    }

    /**
     * 평가사가 진단을 끝내야 mileage 랑 estimatedPrice를 채우므로 이 판정이 곧 승인 여부다.
     */
    public boolean isDiagnosed() {
        return mileage != null && estimatedPrice != null;
    }
}
