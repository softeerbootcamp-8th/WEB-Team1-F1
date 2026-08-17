package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.evaluation.application.dto.command.EvaluationRejectCommand;
import com.softeer.race.evaluation.application.dto.command.EvaluationResultSubmitCommand;
import com.softeer.race.evaluation.application.dto.info.EvaluationRejectionInfo;
import com.softeer.race.evaluation.application.dto.info.EvaluationResultInfo;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.domain.EvaluationStatus;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.notification.application.NotificationPublisher;
import com.softeer.race.notification.domain.NotificationContent;
import com.softeer.race.storage.domain.FileCategory;
import com.softeer.race.storage.domain.FileStorage;
import com.softeer.race.user.domain.User;
import com.softeer.race.vehicle.application.VehicleImageService;
import com.softeer.race.vehicle.application.dto.command.VehicleImageRegisterCommand;
import com.softeer.race.vehicle.application.dto.info.VehicleImageRegisterInfo;
import com.softeer.race.vehicle.application.VehicleKeywordService;
import com.softeer.race.vehicle.domain.Manufacturer;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleImageRepository;
import com.softeer.race.vehicle.domain.VehicleKeyword;
import com.softeer.race.vehicle.domain.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * 시나리오
 * <ol>
 *   <li>제출 한 번에 차량·사진·진단서·상태가 함께 반영된다</li>
 *   <li>세션에서 온 요청자가 자격 판정으로 그대로 넘어간다</li>
 *   <li>재제출은 차량의 진단서를 새 주소로 덮는다</li>
 *   <li>진단서 주소가 문서가 아니면 아무것도 건드리지 않는다</li>
 *   <li>없는 평가면 NOT_FOUND</li>
 *   <li>승인이 확정되면 판매자에게 등록 안내 알림이 간다</li>
 *   <li>반려는 사유를 남기고 판매자에게 알림을 보내되, 차량과 사진은 건드리지 않는다</li>
 * </ol>
 * <p>
 * 담당자·상태 판정 자체는 여기서 확인하지 않는다. 그 규칙은 {@code Evaluation}이 들고 있어
 * {@code EvaluationTest}가 직접 확인하고, 이 서비스가 할 일은 조립 순서와 위임이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("평가 결과 제출 서비스")
class EvaluationResultServiceTest {

    private static final long EVALUATION_ID = 600L;
    private static final long EVALUATOR_ID = 601L;
    private static final long SELLER_ID = 602L;
    private static final long VEHICLE_ID = 6000L;

    private static final int MILEAGE = 45_000;
    private static final long ESTIMATED_PRICE = 21_500_000L;

    private static final String IMAGE_1 = "https://cdn.race.dev/images/2026/08/a.jpg";
    private static final String IMAGE_2 = "https://cdn.race.dev/images/2026/08/b.jpg";
    private static final String DOCUMENT_URL = "https://cdn.race.dev/documents/2026/08/c.pdf";
    private static final String NEW_DOCUMENT_URL = "https://cdn.race.dev/documents/2026/08/d.pdf";
    private static final String REJECT_REASON = "번호판이 등록된 차량과 일치하지 않습니다.";

    private static final List<VehicleKeyword> KEYWORDS =
            List.of(VehicleKeyword.ACCIDENT_FREE, VehicleKeyword.UNDERBODY_INTACT);

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private VehicleImageService vehicleImageService;

    /* 제출 경로는 쓰지 않지만 생성자 인자라 둔다. 없으면 서비스에 null이 주입된다 */
    @Mock
    private VehicleImageRepository vehicleImageRepository;

    @Mock
    private VehicleKeywordService vehicleKeywordService;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private EvaluationResultService evaluationResultService;

    private Evaluation evaluation;
    private Vehicle vehicle;
    private User seller;

    @BeforeEach
    void before() {
        vehicle = mock(Vehicle.class);
        evaluation = mock(Evaluation.class);
        seller = mock(User.class);

        // 알림 문구가 차량 이름과 번호판을 담는다, 발행이 없는 경로도 있어 lenient 로 둔다
        lenient().when(vehicle.getManufacturer()).thenReturn(Manufacturer.HYUNDAI);
        lenient().when(vehicle.getModel()).thenReturn("아반떼 CN7");
        lenient().when(vehicle.getPlateNumber()).thenReturn("60가6000");
    }

    @Test
    @DisplayName("제출 한 번에 차량·사진·진단서·상태가 함께 반영된다")
    void submit() {
        // given
        givenManagedDocument(DOCUMENT_URL);
        givenEvaluationFound();
        givenImagesRegistered();
        givenKeywordsReplaced();
        given(vehicle.getDiagnosticReportUrl()).willReturn(DOCUMENT_URL);
        given(evaluation.getStatus()).willReturn(EvaluationStatus.REQUESTED);
        willAnswer(invocation -> {
            given(evaluation.getStatus()).willReturn(EvaluationStatus.APPROVED);
            return null;
        }).given(evaluation).approve();

        // when
        EvaluationResultInfo info = evaluationResultService.submit(command(DOCUMENT_URL));

        // then : 넷 중 하나라도 빠지면 반쪽짜리 차량이 남는다.
        //        특히 차량 갱신이 빠지면 주행거리가 빈 차가 경매로 넘어간다
        then(vehicle).should().completeDiagnosis(MILEAGE, ESTIMATED_PRICE, IMAGE_1, DOCUMENT_URL);
        then(vehicleImageService).should().register(any(VehicleImageRegisterCommand.class));
        then(evaluation).should().approve();

        assertThat(info.evaluationId()).isEqualTo(EVALUATION_ID);
        assertThat(info.vehicleId()).isEqualTo(VEHICLE_ID);
        assertThat(info.mileage()).isEqualTo(MILEAGE);
        assertThat(info.estimatedPrice()).isEqualTo(ESTIMATED_PRICE);
        assertThat(info.imageUrls()).containsExactly(IMAGE_1, IMAGE_2);
        assertThat(info.diagnosticReportUrl()).isEqualTo(DOCUMENT_URL);
        assertThat(info.status()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("세션에서 온 요청자가 자격 판정으로 그대로 넘어간다")
    void submitDelegatesAuthorization() {
        // given
        givenManagedDocument(DOCUMENT_URL);
        givenEvaluationFound();
        givenImagesRegistered();
        givenKeywordsReplaced();
        given(evaluation.getStatus()).willReturn(EvaluationStatus.APPROVED);

        // when
        evaluationResultService.submit(command(DOCUMENT_URL));

        // then : 다른 값이 넘어가면 "배정된 평가사만"이라는 규칙이 무의미해진다
        then(evaluation).should().validateDiagnosableBy(EVALUATOR_ID);
    }

    @Test
    @DisplayName("사진은 그 차량의 것으로 순서 그대로 넘긴다")
    void submitPassesImagesToVehicle() {
        // given
        givenManagedDocument(DOCUMENT_URL);
        givenEvaluationFound();
        givenImagesRegistered();
        givenKeywordsReplaced();
        given(evaluation.getStatus()).willReturn(EvaluationStatus.APPROVED);

        // when
        evaluationResultService.submit(command(DOCUMENT_URL));

        // then : 순서가 표시 순서이고 첫 장이 대표 이미지가 된다
        ArgumentCaptor<VehicleImageRegisterCommand> captor =
                ArgumentCaptor.forClass(VehicleImageRegisterCommand.class);
        then(vehicleImageService).should().register(captor.capture());

        assertThat(captor.getValue().vehicleId()).isEqualTo(VEHICLE_ID);
        assertThat(captor.getValue().imageUrls()).containsExactly(IMAGE_1, IMAGE_2);
    }

    @Test
    @DisplayName("재제출은 차량의 진단서를 새 주소로 덮는다")
    void submitReplacesExistingReport() {
        // given : 잘못 올린 진단서를 고치는 흐름이다
        givenManagedDocument(NEW_DOCUMENT_URL);
        givenEvaluationFound();
        givenImagesRegistered();
        givenKeywordsReplaced();
        given(vehicle.getDiagnosticReportUrl()).willReturn(NEW_DOCUMENT_URL);
        given(evaluation.getStatus()).willReturn(EvaluationStatus.APPROVED);

        // when
        EvaluationResultInfo info = evaluationResultService.submit(command(NEW_DOCUMENT_URL));

        // then
        then(vehicle).should().completeDiagnosis(MILEAGE, ESTIMATED_PRICE, IMAGE_1, NEW_DOCUMENT_URL);
        assertThat(info.diagnosticReportUrl()).isEqualTo(NEW_DOCUMENT_URL);
    }

    @Test
    @DisplayName("경매가 등록된 차량은 결과를 다시 제출할 수 없다")
    void submitRejectsResultLockedByAuction() {
        givenManagedDocument(NEW_DOCUMENT_URL);
        given(evaluationRepository.findByIdForUpdate(EVALUATION_ID)).willReturn(Optional.of(evaluation));
        given(evaluation.getVehicle()).willReturn(vehicle);
        given(vehicle.getId()).willReturn(VEHICLE_ID);
        given(vehicleRepository.findByIdForUpdate(VEHICLE_ID)).willReturn(Optional.of(vehicle));
        given(auctionRepository.existsByVehicleId(VEHICLE_ID)).willReturn(true);

        assertThatThrownBy(() -> evaluationResultService.submit(command(NEW_DOCUMENT_URL)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.RESULT_LOCKED_BY_AUCTION));

        then(vehicle).should(never()).completeDiagnosis(anyInt(), anyLong(), any(), any());
        then(vehicleImageService).shouldHaveNoInteractions();
        then(notificationPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("진단서 주소가 문서가 아니면 아무것도 건드리지 않는다")
    void submitRejectsUnmanagedDocument() {
        // given : 종류를 DOCUMENT로 묻지 않으면 차량 사진이 진단서 자리에 박힌다
        given(fileStorage.isManagedUrl(IMAGE_1, FileCategory.DOCUMENT)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> evaluationResultService.submit(command(IMAGE_1)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.UNMANAGED_DOCUMENT_URL));

        // 주소 검증을 맨 앞에 둔 이유가 여기 있다.
        // 사진을 먼저 갈아 끼웠다면 기존 사진이 지워진 채로 400이 나갔을 것이다
        then(evaluationRepository).shouldHaveNoInteractions();
        then(vehicleImageService).shouldHaveNoInteractions();

        // 승인되지 않았으니 승인 알림도 없어야 한다
        then(notificationPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("없는 평가면 NOT_FOUND")
    void submitRejectsUnknownEvaluation() {
        // given
        givenManagedDocument(DOCUMENT_URL);
        given(evaluationRepository.findByIdForUpdate(EVALUATION_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> evaluationResultService.submit(command(DOCUMENT_URL)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(EvaluationErrorCode.NOT_FOUND));

        then(vehicleImageService).shouldHaveNoInteractions();
        then(notificationPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("승인이 확정되면 판매자에게 등록 안내 알림이 간다")
    void submitNotifiesSeller() {
        // given
        givenManagedDocument(DOCUMENT_URL);
        givenEvaluationFound();
        givenImagesRegistered();
        givenKeywordsReplaced();
        given(evaluation.getStatus()).willReturn(EvaluationStatus.APPROVED);

        // when
        evaluationResultService.submit(command(DOCUMENT_URL));

        // then : 받는 사람은 제출자인 평가사가 아니라 판매자다.
        //        참조가 신청 건이라야 등록 화면이 차량과 산정 시세를 찾아간다
        then(notificationPublisher).should().publishContent(SELLER_ID,
                NotificationContent.evaluationApproved("현대 아반떼 CN7", "60가6000"), EVALUATION_ID);
    }

    @Test
    @DisplayName("반려하면 사유가 남고 판매자에게 알림이 가며 차량과 사진은 건드리지 않는다")
    void reject() {
        // given
        given(evaluationRepository.findByIdForUpdate(EVALUATION_ID)).willReturn(Optional.of(evaluation));
        given(evaluation.getVehicle()).willReturn(vehicle);
        given(vehicle.getSeller()).willReturn(seller);
        given(seller.getId()).willReturn(SELLER_ID);
        given(evaluation.getId()).willReturn(EVALUATION_ID);
        given(evaluation.getStatus()).willReturn(EvaluationStatus.REJECTED);
        given(evaluation.getRejectReason()).willReturn(REJECT_REASON);

        // when
        EvaluationRejectionInfo info = evaluationResultService.reject(rejectCommand());

        // then : 자격 판정에 세션에서 온 요청자가 그대로 넘어가고, 통과한 뒤에야 상태가 바뀐다
        then(evaluation).should().validateRejectableBy(EVALUATOR_ID);
        then(evaluation).should().reject(REJECT_REASON);

        // 받는 사람은 반려한 평가사가 아니라 판매자다.
        // 참조가 신청 건이라야 알림을 눌렀을 때 사유가 있는 상세로 간다
        then(notificationPublisher).should().publishContent(SELLER_ID,
                NotificationContent.evaluationRejected("현대 아반떼 CN7", "60가6000"), EVALUATION_ID);

        // 반려는 차량을 건드리지 않는다. 진단 전 상태로 남아야 같은 번호판 재신청이 성립한다
        then(vehicleImageService).shouldHaveNoInteractions();
        then(vehicle).should(never()).completeDiagnosis(anyInt(), anyLong(), any(), any());

        assertThat(info.evaluationId()).isEqualTo(EVALUATION_ID);
        assertThat(info.status()).isEqualTo(EvaluationStatus.REJECTED.name());
        assertThat(info.rejectReason()).isEqualTo(REJECT_REASON);
    }

    // 승인 제출과 같은 이유로 잠그고 읽는다. findById로 바꾸면 승인과 반려가 동시에 들어올 때
    // 둘 다 REQUESTED를 읽고 통과해, 나중 쓰기가 앞의 판정을 조용히 덮는다
    @Test
    @DisplayName("반려도 평가 행을 잠그고 읽는다")
    void rejectLocksEvaluation() {
        given(evaluationRepository.findByIdForUpdate(EVALUATION_ID)).willReturn(Optional.of(evaluation));
        given(evaluation.getVehicle()).willReturn(vehicle);
        given(vehicle.getSeller()).willReturn(seller);
        given(seller.getId()).willReturn(SELLER_ID);
        given(evaluation.getStatus()).willReturn(EvaluationStatus.REJECTED);

        evaluationResultService.reject(rejectCommand());

        then(evaluationRepository).should().findByIdForUpdate(EVALUATION_ID);
        then(evaluationRepository).should(never()).findById(EVALUATION_ID);
    }

    @Test
    @DisplayName("없는 평가를 반려하면 NOT_FOUND")
    void rejectRejectsMissingEvaluation() {
        given(evaluationRepository.findByIdForUpdate(EVALUATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> evaluationResultService.reject(rejectCommand()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(EvaluationErrorCode.NOT_FOUND));

        then(notificationPublisher).shouldHaveNoInteractions();
    }

    private static EvaluationRejectCommand rejectCommand() {
        return new EvaluationRejectCommand(EVALUATION_ID, EVALUATOR_ID, REJECT_REASON);
    }

    private void givenManagedDocument(String fileUrl) {
        given(fileStorage.isManagedUrl(fileUrl, FileCategory.DOCUMENT)).willReturn(true);
    }

    private void givenEvaluationFound() {
        given(evaluationRepository.findByIdForUpdate(EVALUATION_ID)).willReturn(Optional.of(evaluation));
        given(evaluation.getVehicle()).willReturn(vehicle);
        given(vehicle.getId()).willReturn(VEHICLE_ID);
        given(vehicleRepository.findByIdForUpdate(VEHICLE_ID)).willReturn(Optional.of(vehicle));
        given(vehicle.getSeller()).willReturn(seller);
        given(seller.getId()).willReturn(SELLER_ID);
    }

    private void givenKeywordsReplaced() {
        given(vehicleKeywordService.replace(any(Vehicle.class), anyList())).willReturn(KEYWORDS);
    }

    private void givenImagesRegistered() {
        given(vehicleImageService.register(any(VehicleImageRegisterCommand.class)))
                .willReturn(new VehicleImageRegisterInfo(VEHICLE_ID, List.of(
                        new VehicleImageRegisterInfo.RegisteredImage(IMAGE_1, 1),
                        new VehicleImageRegisterInfo.RegisteredImage(IMAGE_2, 2))));
    }

    private static EvaluationResultSubmitCommand command(String diagnosticReportUrl) {
        return new EvaluationResultSubmitCommand(EVALUATION_ID, EVALUATOR_ID,
                MILEAGE, ESTIMATED_PRICE, List.of(IMAGE_1, IMAGE_2), diagnosticReportUrl,
                KEYWORDS);
    }
}
