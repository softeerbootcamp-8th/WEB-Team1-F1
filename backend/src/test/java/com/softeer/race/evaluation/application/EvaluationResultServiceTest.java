package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.command.EvaluationResultSubmitCommand;
import com.softeer.race.evaluation.application.dto.info.EvaluationResultInfo;
import com.softeer.race.evaluation.domain.DiagnosticReport;
import com.softeer.race.evaluation.domain.DiagnosticReportRepository;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.domain.EvaluationStatus;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.storage.domain.FileCategory;
import com.softeer.race.storage.domain.FileStorage;
import com.softeer.race.vehicle.application.VehicleImageService;
import com.softeer.race.vehicle.application.dto.command.VehicleImageRegisterCommand;
import com.softeer.race.vehicle.application.dto.info.VehicleImageRegisterInfo;
import com.softeer.race.vehicle.domain.Vehicle;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * 시나리오
 * <ol>
 *   <li>제출 한 번에 차량·사진·진단서·상태가 함께 반영된다</li>
 *   <li>세션에서 온 요청자가 자격 판정으로 그대로 넘어간다</li>
 *   <li>재제출은 진단서를 새로 만들지 않고 갈아 끼운다</li>
 *   <li>진단서 주소가 문서가 아니면 아무것도 건드리지 않는다</li>
 *   <li>없는 평가면 NOT_FOUND</li>
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
    private static final long VEHICLE_ID = 6000L;

    private static final int MILEAGE = 45_000;
    private static final long ESTIMATED_PRICE = 21_500_000L;

    private static final String IMAGE_1 = "https://cdn.race.dev/images/2026/08/a.jpg";
    private static final String IMAGE_2 = "https://cdn.race.dev/images/2026/08/b.jpg";
    private static final String DOCUMENT_URL = "https://cdn.race.dev/documents/2026/08/c.pdf";
    private static final String NEW_DOCUMENT_URL = "https://cdn.race.dev/documents/2026/08/d.pdf";

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private DiagnosticReportRepository diagnosticReportRepository;

    @Mock
    private VehicleImageService vehicleImageService;

    @Mock
    private FileStorage fileStorage;

    @InjectMocks
    private EvaluationResultService evaluationResultService;

    private Evaluation evaluation;
    private Vehicle vehicle;

    @BeforeEach
    void before() {
        vehicle = mock(Vehicle.class);
        evaluation = mock(Evaluation.class);
    }

    @Test
    @DisplayName("제출 한 번에 차량·사진·진단서·상태가 함께 반영된다")
    void submit() {
        // given
        givenManagedDocument(DOCUMENT_URL);
        givenEvaluationFound();
        givenImagesRegistered();
        given(diagnosticReportRepository.findByEvaluationId(EVALUATION_ID))
                .willReturn(Optional.empty());
        given(diagnosticReportRepository.save(any(DiagnosticReport.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(evaluation.getStatus()).willReturn(EvaluationStatus.APPROVED);

        // when
        EvaluationResultInfo info = evaluationResultService.submit(command(DOCUMENT_URL));

        // then : 넷 중 하나라도 빠지면 반쪽짜리 차량이 남는다.
        //        특히 차량 갱신이 빠지면 주행거리가 빈 차가 경매로 넘어간다
        then(vehicle).should().completeDiagnosis(MILEAGE, ESTIMATED_PRICE);
        then(vehicleImageService).should().register(any(VehicleImageRegisterCommand.class));
        then(diagnosticReportRepository).should().save(any(DiagnosticReport.class));
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
        given(diagnosticReportRepository.findByEvaluationId(EVALUATION_ID))
                .willReturn(Optional.empty());
        given(diagnosticReportRepository.save(any(DiagnosticReport.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
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
        given(diagnosticReportRepository.findByEvaluationId(EVALUATION_ID))
                .willReturn(Optional.empty());
        given(diagnosticReportRepository.save(any(DiagnosticReport.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
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
    @DisplayName("재제출은 진단서를 새로 만들지 않고 갈아 끼운다")
    void submitReplacesExistingReport() {
        // given : 잘못 올린 진단서를 고치는 흐름이다
        DiagnosticReport existing =
                DiagnosticReport.attach(mock(Evaluation.class), DOCUMENT_URL);
        givenManagedDocument(NEW_DOCUMENT_URL);
        givenEvaluationFound();
        givenImagesRegistered();
        given(diagnosticReportRepository.findByEvaluationId(EVALUATION_ID))
                .willReturn(Optional.of(existing));
        given(evaluation.getStatus()).willReturn(EvaluationStatus.APPROVED);

        // when
        EvaluationResultInfo info = evaluationResultService.submit(command(NEW_DOCUMENT_URL));

        // then : evaluation_id가 unique라 새 행을 만들면 제약에 걸린다.
        then(diagnosticReportRepository).should(never()).save(any());
        assertThat(existing.getFileUrl()).isEqualTo(NEW_DOCUMENT_URL);
        assertThat(info.diagnosticReportUrl()).isEqualTo(NEW_DOCUMENT_URL);
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
    }

    private void givenManagedDocument(String fileUrl) {
        given(fileStorage.isManagedUrl(fileUrl, FileCategory.DOCUMENT)).willReturn(true);
    }

    private void givenEvaluationFound() {
        given(evaluationRepository.findByIdForUpdate(EVALUATION_ID)).willReturn(Optional.of(evaluation));
        given(evaluation.getVehicle()).willReturn(vehicle);
        given(vehicle.getId()).willReturn(VEHICLE_ID);
    }

    private void givenImagesRegistered() {
        given(vehicleImageService.register(any(VehicleImageRegisterCommand.class)))
                .willReturn(new VehicleImageRegisterInfo(VEHICLE_ID, List.of(
                        new VehicleImageRegisterInfo.RegisteredImage(IMAGE_1, 1),
                        new VehicleImageRegisterInfo.RegisteredImage(IMAGE_2, 2)),
                        IMAGE_1));
    }

    private static EvaluationResultSubmitCommand command(String diagnosticReportUrl) {
        return new EvaluationResultSubmitCommand(EVALUATION_ID, EVALUATOR_ID,
                MILEAGE, ESTIMATED_PRICE, List.of(IMAGE_1, IMAGE_2), diagnosticReportUrl);
    }
}
