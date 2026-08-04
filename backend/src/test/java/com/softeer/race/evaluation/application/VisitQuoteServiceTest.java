package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.command.VisitQuoteCommand;
import com.softeer.race.evaluation.application.dto.info.VisitQuoteInfo;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.domain.EvaluationStatus;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.user.domain.User;
import com.softeer.race.user.domain.UserRepository;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleLookup;
import com.softeer.race.vehicle.domain.VehicleRepository;
import com.softeer.race.vehicle.domain.VehicleSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("방문견적 신청 서비스")
class VisitQuoteServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-04T02:31:17.123456Z"), KST);
    private static final LocalDate TODAY = LocalDate.now(FIXED_CLOCK);

    private static final long SELLER_ID = 90L;
    private static final String PLATE_NUMBER = "12가3456";
    private static final String OWNER_NAME = "김민수";
    private static final String VISIT_ADDRESS = "서울 성동구 왕십리로 83";
    private static final String CONTACT_PHONE = "01012345678";

    /** 그 모델의 기준가. 접수 시점에는 쓰지 않는다 — 시세는 평가사가 방문해 산정한다 */
    private static final long BASE_PRICE = 34_000_000L;

    @Mock
    private VehicleLookup vehicleLookup;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private EvaluationRepository evaluationRepository;

    private VisitQuoteService service;

    @BeforeEach
    void before() {
        service = new VisitQuoteService(vehicleLookup, userRepository, vehicleRepository,
                evaluationRepository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("번호판으로 조회한 제원으로 차량을 등록하고 배정 대기 상태의 신청을 만든다")
    void request() {
        // given
        givenNoDuplicate();
        givenLookup();
        givenSeller();
        givenSaveReturnsArgument();

        // when
        VisitQuoteInfo info = service.request(command(TODAY.plusDays(16)));

        // then 1 : 신청은 접수 상태이고 평가사가 배정되지 않았다
        Evaluation evaluation = capturedEvaluation();
        assertThat(evaluation.getStatus()).isEqualTo(EvaluationStatus.REQUESTED);
        assertThat(evaluation.getEvaluator()).isNull();

        // then 2 : 방문 정보가 요청 그대로 남는다
        assertThat(evaluation.getVisitDate()).isEqualTo(TODAY.plusDays(16));
        assertThat(evaluation.getVisitAddress()).isEqualTo(VISIT_ADDRESS);
        assertThat(evaluation.getContactPhone()).isEqualTo(CONTACT_PHONE);

        // then 3 : 차량은 클라이언트가 보내지 않은 제원까지 서버가 조회해 채웠다
        Vehicle vehicle = capturedVehicle();
        assertThat(vehicle.getPlateNumber()).isEqualTo(PLATE_NUMBER);
        assertThat(vehicle.getModelYear()).isEqualTo(2021);
        assertThat(evaluation.getVehicle()).isSameAs(vehicle);

        // then 4 : 주행거리와 예상 시세는 비어 있다 — 실측과 산정은 평가사가 방문해서 한다
        // 여기에 값이 들어가면 검증되지 않은 숫자가 차량에 남고 화면이 그것을 견적으로 읽는다
        assertThat(vehicle.getMileage()).isNull();
        assertThat(vehicle.getEstimatedPrice()).isNull();
        assertThat(BASE_PRICE).isPositive();  // 기준가를 조회했지만 쓰지 않는다는 것을 남긴다

        // then 5 : 응답에도 금액이 없다
        assertThat(info.status()).isEqualTo("REQUESTED");
        assertThat(info.plateNumber()).isEqualTo(PLATE_NUMBER);
    }

    // 중복 판정 기준이 vehicle_id가 아니라 번호판이어야 한다는 결정을 고정한다
    // vehicle_id로 묶으면 방금 만든 차량만 보게 되어 중복이 전부 통과한다
    @Test
    @DisplayName("중복 검사는 번호판과 진행 중 상태로만 조회한다")
    void requestChecksDuplicateByPlateNumberAndInProgressStatuses() {
        givenNoDuplicate();
        givenLookup();
        givenSeller();
        givenSaveReturnsArgument();

        service.request(command(TODAY.plusDays(16)));

        then(evaluationRepository).should()
                .existsByVehiclePlateNumberAndStatusIn(PLATE_NUMBER, EvaluationStatus.inProgress());
    }

    @Test
    @DisplayName("진행 중인 신청이 있으면 차량을 만들기 전에 409 코드로 거부한다")
    void requestRejectsDuplicate() {
        given(evaluationRepository.existsByVehiclePlateNumberAndStatusIn(eq(PLATE_NUMBER), any()))
                .willReturn(true);

        assertThatThrownBy(() -> service.request(command(TODAY.plusDays(16))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.DUPLICATE_REQUEST));

        // 거부될 요청에 카탈로그 조회와 insert를 태우지 않는다는 순서 결정을 고정한다
        then(vehicleLookup).shouldHaveNoInteractions();
        then(vehicleRepository).shouldHaveNoInteractions();
        then(evaluationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("번호판이나 소유자명이 어긋나면 아무것도 저장하지 않고 404 코드로 거부한다")
    void requestUnknownPlateNumber() {
        givenNoDuplicate();
        given(vehicleLookup.find(PLATE_NUMBER, OWNER_NAME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(command(TODAY.plusDays(16))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.VEHICLE_NOT_FOUND));

        then(vehicleRepository).shouldHaveNoInteractions();
    }

    // getReferenceById였다면 여기서 걸리지 않고 flush 시점의 FK 위반 500이 된다
    @Test
    @DisplayName("판매자 계정이 없으면 차량을 만들기 전에 거부한다")
    void requestUnknownSeller() {
        givenNoDuplicate();
        givenLookup();
        given(userRepository.findById(SELLER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(command(TODAY.plusDays(16))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.SELLER_NOT_FOUND));

        then(vehicleRepository).shouldHaveNoInteractions();
    }

    // 방문일 검증을 저장 전에 두기로 한 순서 결정을 고정한다
    // Evaluation.request를 vehicleRepository.save 뒤로 옮기면 이 테스트가 깨진다
    @Test
    @DisplayName("과거 날짜는 차량을 저장하기 전에 거부한다")
    void requestRejectsPastVisitDate() {
        givenNoDuplicate();
        givenLookup();
        givenSeller();

        assertThatThrownBy(() -> service.request(command(TODAY.minusDays(1))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.PAST_VISIT_DATE));

        then(vehicleRepository).should(never()).save(any());
        then(evaluationRepository).should(never()).save(any());
    }

    // ================= given =================

    private void givenNoDuplicate() {
        given(evaluationRepository.existsByVehiclePlateNumberAndStatusIn(eq(PLATE_NUMBER), any()))
                .willReturn(false);
    }

    private void givenLookup() {
        given(vehicleLookup.find(PLATE_NUMBER, OWNER_NAME)).willReturn(Optional.of(spec()));
    }

    private void givenSeller() {
        given(userRepository.findById(SELLER_ID)).willReturn(Optional.of(mock(User.class)));
    }

    // save가 돌려주는 엔티티에는 id가 없으므로 반환 Info의 id가 아니라 captor로 검증한다
    private void givenSaveReturnsArgument() {
        given(vehicleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(evaluationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    private static VisitQuoteCommand command(LocalDate visitDate) {
        return new VisitQuoteCommand(SELLER_ID, PLATE_NUMBER, OWNER_NAME,
                VISIT_ADDRESS, visitDate, CONTACT_PHONE);
    }

    private static VehicleSpec spec() {
        return new VehicleSpec(PLATE_NUMBER, OWNER_NAME,
                Manufacturer.HYUNDAI, "그랜저 IG", 2021,
                FuelType.GASOLINE, Transmission.AUTOMATIC,
                BASE_PRICE, "https://cdn.race.dev/vehicles/grandeur-ig.jpg");
    }

    // ================= 캡처 =================

    private Vehicle capturedVehicle() {
        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        then(vehicleRepository).should().save(captor.capture());
        return captor.getValue();
    }

    private Evaluation capturedEvaluation() {
        ArgumentCaptor<Evaluation> captor = ArgumentCaptor.forClass(Evaluation.class);
        then(evaluationRepository).should().save(captor.capture());
        return captor.getValue();
    }
}
