package com.softeer.race.evaluation.application;

import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.auction.domain.VehicleAuctionStatusRow;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.info.EvaluationDetailInfo;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.domain.EvaluationStatus;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.vehicle.domain.FuelType;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Transmission;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleImage;
import com.softeer.race.vehicle.application.VehicleKeywordService;
import com.softeer.race.vehicle.domain.VehicleImageRepository;
import com.softeer.race.vehicle.domain.VehicleKeyword;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * 시나리오
 * <ol>
 *   <li>내 신청 목록은 레포지토리가 준 순서를 그대로 옮긴다</li>
 *   <li>진단이 끝난 상세는 결과 칸이 채워져 나간다</li>
 *   <li>진단 전 상세는 결과 칸이 전부 null이다</li>
 *   <li>열람 권한이 없으면 존재 여부를 감춘 NOT_FOUND</li>
 *   <li>없는 신청도 NOT_FOUND</li>
 * </ol>
 * <p>
 * 목록의 필터링과 정렬은 여기서 확인하지 않는다. 그건 JPQL이 하는 일이라 목으로는 재현할 수 없고,
 * 실제 DB로 도는 통합 테스트가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("방문견적 조회 서비스")
class EvaluationLookupServiceTest {

    private static final long EVALUATION_ID = 600L;
    private static final long SELLER_ID = 600L;
    private static final long EVALUATOR_ID = 601L;
    private static final long STRANGER_ID = 603L;
    private static final long VEHICLE_ID = 6000L;

    private static final String IMAGE_URL = "https://cdn.race.dev/images/2026/08/a.jpg";
    private static final String DOCUMENT_URL = "https://cdn.race.dev/documents/2026/08/c.pdf";

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private VehicleImageRepository vehicleImageRepository;

    @Mock
    private VehicleKeywordService vehicleKeywordService;

    @InjectMocks
    private EvaluationLookupService evaluationLookupService;

    private Evaluation evaluation;
    private Vehicle vehicle;

    @BeforeEach
    void before() {
        vehicle = mock(Vehicle.class);
        evaluation = mock(Evaluation.class);
    }

    @Test
    @DisplayName("내 신청 목록은 레포지토리가 준 순서를 그대로 옮긴다")
    void findMyRequests() {
        // given : 정렬은 JPQL이 하고 서비스는 그 순서를 흔들지 않아야 한다.
        //         목을 given 인자 안에서 만들면 Mockito가 스터빙이 끝나지 않은 것으로 본다
        List<Evaluation> found = List.of(summaryOf(700L), summaryOf(600L));
        given(evaluationRepository.findBySellerId(SELLER_ID)).willReturn(found);

        // when & then
        assertThat(evaluationLookupService.findMyRequests(SELLER_ID))
                .extracting(info -> info.evaluationId())
                .containsExactly(700L, 600L);
    }

    @Test
    @DisplayName("평가 목록에 차량의 최신 경매 상태를 붙인다")
    void findMyRequestsIncludesLatestAuctionStatus() {
        List<Evaluation> found = List.of(summaryOf(700L), summaryOf(600L));
        given(evaluationRepository.findBySellerId(SELLER_ID)).willReturn(found);
        given(auctionRepository.findLatestStatusesByVehicleIdIn(List.of(700L, 600L)))
                .willReturn(List.of(new VehicleAuctionStatusRow(700L, AuctionStatus.IN_PROGRESS)));

        assertThat(evaluationLookupService.findMyRequests(SELLER_ID))
                .extracting(info -> info.auctionStatus())
                .containsExactly(AuctionStatus.IN_PROGRESS, null);
    }

    @Test
    @DisplayName("평가 목록이 비면 경매 상태를 조회하지 않는다")
    void emptyAssignmentsSkipAuctionLookup() {
        given(evaluationRepository.findByEvaluatorId(EVALUATOR_ID)).willReturn(List.of());

        assertThat(evaluationLookupService.findMyAssignments(EVALUATOR_ID)).isEmpty();

        then(auctionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("진단이 끝난 상세는 결과 칸이 채워져 나간다")
    void findDetailAfterDiagnosis() {
        // given
        givenViewableEvaluation();
        givenVehicleSpec();
        given(vehicle.getMileage()).willReturn(45_000);
        given(vehicle.getEstimatedPrice()).willReturn(21_500_000L);
        List<VehicleImage> images = List.of(imageOf(IMAGE_URL));
        given(vehicleImageRepository.findAllByVehicleOrderBySortOrderAsc(vehicle))
                .willReturn(images);
        given(vehicle.getDiagnosticReportUrl()).willReturn(DOCUMENT_URL);
        given(vehicleKeywordService.findByVehicle(vehicle))
                .willReturn(List.of(VehicleKeyword.ACCIDENT_FREE, VehicleKeyword.NO_LEAK));

        // when
        EvaluationDetailInfo info = evaluationLookupService.findDetail(EVALUATION_ID, SELLER_ID);

        // then
        assertThat(info.mileage()).isEqualTo(45_000);
        assertThat(info.estimatedPrice()).isEqualTo(21_500_000L);
        assertThat(info.imageUrls()).containsExactly(IMAGE_URL);
        assertThat(info.diagnosticReportUrl()).isEqualTo(DOCUMENT_URL);
        assertThat(info.keywords())
                .containsExactly(VehicleKeyword.ACCIDENT_FREE, VehicleKeyword.NO_LEAK);
    }

    /**
     * 진단 전 차량은 주행거리와 시세가 실제로 비어 있다. Info가 원시 타입으로 받으면 여기서
     * 언박싱 NPE가 나므로 {@code Integer} · {@code Long}으로 둔 결정을 이 테스트가 고정한다.
     */
    @Test
    @DisplayName("진단 전 상세는 결과 칸이 전부 null이다")
    void findDetailBeforeDiagnosis() {
        // given
        givenViewableEvaluation();
        givenVehicleSpec();
        given(vehicle.getMileage()).willReturn(null);
        given(vehicle.getEstimatedPrice()).willReturn(null);
        given(vehicleImageRepository.findAllByVehicleOrderBySortOrderAsc(vehicle))
                .willReturn(List.of());
        given(vehicleKeywordService.findByVehicle(vehicle)).willReturn(List.of());

        // when
        EvaluationDetailInfo info = evaluationLookupService.findDetail(EVALUATION_ID, SELLER_ID);

        // then : 비어 있음이 곧 "아직 평가사가 다녀가지 않았다"이다
        assertThat(info.mileage()).isNull();
        assertThat(info.estimatedPrice()).isNull();
        assertThat(info.diagnosticReportUrl()).isNull();
        assertThat(info.submittedAt()).isNull();
        assertThat(info.imageUrls()).isEmpty();
        // 키워드만 null 이 아니다. 진단을 마쳤어도 0개일 수 있어 null 로는 두 경우가 구분되지 않는다
        assertThat(info.keywords()).isEmpty();
    }

    @Test
    @DisplayName("열람 권한이 없으면 존재 여부를 감춘 NOT_FOUND")
    void findDetailHidesFromStranger() {
        // given
        given(evaluationRepository.findWithVehicleById(EVALUATION_ID))
                .willReturn(Optional.of(evaluation));
        given(evaluation.isViewableBy(STRANGER_ID)).willReturn(false);

        // when & then : 403으로 구분해 주면 id를 훑어 남의 신청과 방문 주소를 알아낼 수 있다
        assertThatThrownBy(() -> evaluationLookupService.findDetail(EVALUATION_ID, STRANGER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(EvaluationErrorCode.NOT_FOUND));

        then(vehicleImageRepository).shouldHaveNoInteractions();
        then(vehicleKeywordService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("없는 신청도 NOT_FOUND")
    void findDetailRejectsUnknownEvaluation() {
        // given
        given(evaluationRepository.findWithVehicleById(EVALUATION_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> evaluationLookupService.findDetail(EVALUATION_ID, SELLER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(EvaluationErrorCode.NOT_FOUND));
    }

    private void givenViewableEvaluation() {
        given(evaluationRepository.findWithVehicleById(EVALUATION_ID))
                .willReturn(Optional.of(evaluation));
        given(evaluation.isViewableBy(SELLER_ID)).willReturn(true);
        given(evaluation.getVehicle()).willReturn(vehicle);
        given(evaluation.getStatus()).willReturn(EvaluationStatus.REQUESTED);
    }

    private void givenVehicleSpec() {
        given(vehicle.getId()).willReturn(VEHICLE_ID);
        given(vehicle.getPlateNumber()).willReturn("12가3456");
        given(vehicle.getManufacturer()).willReturn(Manufacturer.HYUNDAI);
        given(vehicle.getModel()).willReturn("그랜저 IG");
        given(vehicle.getModelYear()).willReturn(2021);
        given(vehicle.getFuelType()).willReturn(FuelType.GASOLINE);
        given(vehicle.getTransmission()).willReturn(Transmission.AUTOMATIC);
    }

    private static Evaluation summaryOf(long evaluationId) {
        Vehicle vehicle = mock(Vehicle.class);
        given(vehicle.getId()).willReturn(evaluationId);
        given(vehicle.getManufacturer()).willReturn(Manufacturer.HYUNDAI);

        Evaluation evaluation = mock(Evaluation.class);
        given(evaluation.getId()).willReturn(evaluationId);
        given(evaluation.getVehicle()).willReturn(vehicle);
        given(evaluation.getStatus()).willReturn(EvaluationStatus.REQUESTED);

        return evaluation;
    }

    private static VehicleImage imageOf(String imageUrl) {
        VehicleImage image = mock(VehicleImage.class);
        given(image.getImageUrl()).willReturn(imageUrl);

        return image;
    }
}
