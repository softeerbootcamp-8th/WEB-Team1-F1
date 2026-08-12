package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.evaluation.application.dto.command.EvaluationResultPatchCommand;
import com.softeer.race.evaluation.application.dto.info.EvaluationResultInfo;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.domain.EvaluationStatus;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.notification.application.NotificationPublisher;
import com.softeer.race.storage.domain.FileCategory;
import com.softeer.race.storage.domain.FileStorage;
import com.softeer.race.vehicle.application.VehicleImageService;
import com.softeer.race.vehicle.application.VehicleKeywordService;
import com.softeer.race.vehicle.application.dto.command.VehicleImageRegisterCommand;
import com.softeer.race.vehicle.application.dto.info.VehicleImageRegisterInfo;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleImage;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * 시나리오
 * <ol>
 *   <li>보낸 항목만 바뀌고 나머지는 손대지 않는다</li>
 *   <li>사진만 바꿔도 주행거리·시세·진단서를 다시 보낼 필요가 없다</li>
 *   <li>사진 목록이 바뀌면 대표 사진이 새 첫 장으로 따라간다</li>
 *   <li>진단서만 갈아 끼울 수 있다</li>
 *   <li>진단서 주소가 문서가 아니면 아무것도 건드리지 않는다</li>
 *   <li>키워드 빈 배열은 전부 지우고, 보내지 않으면 지금 것을 그대로 둔다</li>
 *   <li>결과가 제출되지 않은 평가는 RESULT_NOT_SUBMITTED</li>
 *   <li>수정에는 승인 알림이 다시 가지 않는다</li>
 *   <li>없는 평가면 NOT_FOUND</li>
 * </ol>
 * <p>
 * 담당자·상태 판정은 여기서 확인하지 않는다. {@code Evaluation}이 들고 있는 규칙이라
 * {@code EvaluationTest}가 직접 확인하고, 이 서비스가 할 일은 "무엇을 건드리고 무엇을 두는가"다.
 * <p>
 * 차량이 목이라 {@code reviseMileage}를 불러도 {@code getMileage}가 따라 바뀌지 않는다. 그래서
 * "새 값이 들어갔는가"는 응답이 아니라 <b>호출로</b> 확인하고, 응답에는 목이 들고 있는 현재 값이
 * 그대로 실리는지를 본다 — 실제로 반영되는지는 통합 테스트가 DB까지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("평가 결과 항목별 수정 서비스")
class EvaluationResultPatchServiceTest {

    private static final long EVALUATION_ID = 600L;
    private static final long EVALUATOR_ID = 601L;
    private static final long VEHICLE_ID = 6000L;

    /** 이미 제출돼 차량에 적혀 있는 값 */
    private static final int MILEAGE = 45_000;
    private static final long ESTIMATED_PRICE = 21_500_000L;

    private static final int NEW_MILEAGE = 46_000;
    private static final long NEW_ESTIMATED_PRICE = 21_000_000L;

    private static final String IMAGE_1 = "https://cdn.race.dev/images/2026/08/a.jpg";
    private static final String IMAGE_2 = "https://cdn.race.dev/images/2026/08/b.jpg";
    private static final String NEW_IMAGE = "https://cdn.race.dev/images/2026/08/c.jpg";
    private static final String NEW_DOCUMENT_URL = "https://cdn.race.dev/documents/2026/08/d.pdf";

    private static final List<VehicleKeyword> KEYWORDS =
            List.of(VehicleKeyword.ACCIDENT_FREE, VehicleKeyword.NO_LEAK);

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private VehicleImageService vehicleImageService;

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

    @BeforeEach
    void before() {
        vehicle = mock(Vehicle.class);
        evaluation = mock(Evaluation.class);
    }

    @Test
    @DisplayName("주행거리만 보내면 주행거리만 바뀐다")
    void patchesOnlyGivenField() {
        // given
        givenDiagnosedEvaluation();
        givenCurrentImages(IMAGE_1, IMAGE_2);
        givenCurrentKeywords();

        // when
        evaluationResultService.patch(command().mileage(NEW_MILEAGE).build());

        // then : 나머지가 함께 불리면 한 항목을 고치려고 전부를 다시 보내야 했던 문제가 그대로다
        then(vehicle).should().reviseMileage(NEW_MILEAGE);
        then(vehicle).should(never()).reviseEstimatedPrice(anyLong());
        then(vehicle).should(never()).replaceDiagnosticReport(anyString());
        then(vehicleImageService).should(never()).register(any());
        then(vehicleKeywordService).should(never()).replace(any(), anyList());
    }

    @Test
    @DisplayName("여러 항목을 함께 보내면 함께 바뀐다")
    void patchesSeveralFields() {
        // given
        givenDiagnosedEvaluation();
        givenCurrentImages(IMAGE_1);
        givenCurrentKeywords();

        // when
        evaluationResultService.patch(command()
                .mileage(NEW_MILEAGE)
                .estimatedPrice(NEW_ESTIMATED_PRICE)
                .build());

        // then
        then(vehicle).should().reviseMileage(NEW_MILEAGE);
        then(vehicle).should().reviseEstimatedPrice(NEW_ESTIMATED_PRICE);
    }

    @Test
    @DisplayName("사진만 바꿔도 나머지 항목은 그대로 남는다")
    void patchesImagesOnly() {
        // given : 낱장을 더하는 흐름 — 기존 목록에 한 장을 더해 보낸다
        givenDiagnosedEvaluation();
        givenImagesReplacedWith(IMAGE_1, IMAGE_2, NEW_IMAGE);
        givenCurrentKeywords();

        // when
        EvaluationResultInfo info = evaluationResultService.patch(
                command().imageUrls(List.of(IMAGE_1, IMAGE_2, NEW_IMAGE)).build());

        // then
        ArgumentCaptor<VehicleImageRegisterCommand> captor =
                ArgumentCaptor.forClass(VehicleImageRegisterCommand.class);
        then(vehicleImageService).should().register(captor.capture());
        assertThat(captor.getValue().vehicleId()).isEqualTo(VEHICLE_ID);
        assertThat(captor.getValue().imageUrls()).containsExactly(IMAGE_1, IMAGE_2, NEW_IMAGE);

        then(vehicle).should(never()).reviseMileage(anyInt());
        then(vehicle).should(never()).reviseEstimatedPrice(anyLong());
        then(vehicle).should(never()).replaceDiagnosticReport(anyString());

        // 응답은 수정 뒤의 결과 전부다. 안 바꾼 항목도 실려 나가야 판매자 화면이 채워진다
        assertThat(info.mileage()).isEqualTo(MILEAGE);
        assertThat(info.estimatedPrice()).isEqualTo(ESTIMATED_PRICE);
        assertThat(info.imageUrls()).containsExactly(IMAGE_1, IMAGE_2, NEW_IMAGE);
        assertThat(info.keywords()).isEqualTo(KEYWORDS);
    }

    @Test
    @DisplayName("첫 장을 빼면 대표 사진이 새 첫 장으로 따라간다")
    void patchKeepsMainPhotoInSync() {
        // given : 대표였던 IMAGE_1을 빼고 나머지만 보낸다
        givenDiagnosedEvaluation();
        givenImagesReplacedWith(IMAGE_2, NEW_IMAGE);
        givenCurrentKeywords();

        // when
        evaluationResultService.patch(command().imageUrls(List.of(IMAGE_2, NEW_IMAGE)).build());

        // then : 이게 빠지면 목록에서 지운 사진이 경매 목록 썸네일에 계속 남는다
        then(vehicle).should().changeMainPhoto(IMAGE_2);
    }

    @Test
    @DisplayName("사진을 보내지 않으면 지금 목록을 읽어 응답에만 싣는다")
    void patchReadsImagesWhenNotGiven() {
        // given
        givenDiagnosedEvaluation();
        givenCurrentImages(IMAGE_1, IMAGE_2);
        givenCurrentKeywords();

        // when
        EvaluationResultInfo info = evaluationResultService.patch(
                command().mileage(NEW_MILEAGE).build());

        // then : 대표 사진도 건드리지 않는다. 목록이 그대로라 다시 맞출 것이 없다
        assertThat(info.imageUrls()).containsExactly(IMAGE_1, IMAGE_2);
        then(vehicle).should(never()).changeMainPhoto(anyString());
    }

    @Test
    @DisplayName("진단서만 갈아 끼울 수 있다")
    void patchesReportOnly() {
        // given
        givenManagedDocument(NEW_DOCUMENT_URL);
        givenDiagnosedEvaluation();
        givenCurrentImages(IMAGE_1);
        givenCurrentKeywords();
        given(vehicle.getDiagnosticReportUrl()).willReturn(NEW_DOCUMENT_URL);

        // when
        EvaluationResultInfo info = evaluationResultService.patch(
                command().diagnosticReportUrl(NEW_DOCUMENT_URL).build());

        // then
        then(vehicle).should().replaceDiagnosticReport(NEW_DOCUMENT_URL);
        then(vehicleImageService).should(never()).register(any());
        assertThat(info.diagnosticReportUrl()).isEqualTo(NEW_DOCUMENT_URL);
    }

    @Test
    @DisplayName("진단서 주소가 문서가 아니면 아무것도 건드리지 않는다")
    void patchRejectsUnmanagedDocument() {
        // given : 종류를 DOCUMENT로 묻지 않으면 차량 사진이 진단서 자리에 박힌다
        given(fileStorage.isManagedUrl(NEW_IMAGE, FileCategory.DOCUMENT)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> evaluationResultService.patch(
                command().diagnosticReportUrl(NEW_IMAGE).imageUrls(List.of(NEW_IMAGE)).build()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.UNMANAGED_DOCUMENT_URL));

        // 주소 검증이 맨 앞이라야 한다. 사진을 먼저 갈아 끼웠다면 기존 사진이 지워진 채 400이 나갔을 것이다
        then(evaluationRepository).shouldHaveNoInteractions();
        then(vehicleImageService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("키워드 빈 배열은 전부 지우고, 보내지 않으면 그대로 둔다")
    void patchDistinguishesEmptyKeywordsFromAbsent() {
        // given
        givenDiagnosedEvaluation();
        givenCurrentImages(IMAGE_1);
        given(vehicleKeywordService.replace(vehicle, List.of())).willReturn(List.of());

        // when
        EvaluationResultInfo info = evaluationResultService.patch(
                command().keywords(List.of()).build());

        // then : 빈 배열을 "안 보냄"으로 받으면 평가사가 뺀 키워드가 그대로 남는다
        then(vehicleKeywordService).should().replace(vehicle, List.of());
        then(vehicleKeywordService).should(never()).findByVehicle(any());
        assertThat(info.keywords()).isEmpty();
    }

    @Test
    @DisplayName("결과가 제출되지 않은 평가는 수정할 수 없다")
    void patchRejectsUndiagnosedVehicle() {
        // given : 배정만 받고 아직 결과를 내지 않은 평가다
        given(evaluationRepository.findByIdForUpdate(EVALUATION_ID))
                .willReturn(Optional.of(evaluation));
        given(evaluation.getVehicle()).willReturn(vehicle);
        given(vehicle.getId()).willReturn(VEHICLE_ID);
        given(vehicleRepository.findByIdForUpdate(VEHICLE_ID)).willReturn(Optional.of(vehicle));
        given(vehicle.isDiagnosed()).willReturn(false);

        // when & then : 이 관문이 없으면 주행거리만 채워지고 시세가 빈 차량이 만들어진다
        assertThatThrownBy(() -> evaluationResultService.patch(
                command().mileage(NEW_MILEAGE).build()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.RESULT_NOT_SUBMITTED));

        then(vehicle).should(never()).reviseMileage(anyInt());
        then(vehicleImageService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("경매가 등록된 차량은 결과를 수정할 수 없다")
    void patchRejectsResultLockedByAuction() {
        given(evaluationRepository.findByIdForUpdate(EVALUATION_ID))
                .willReturn(Optional.of(evaluation));
        given(evaluation.getVehicle()).willReturn(vehicle);
        given(vehicle.getId()).willReturn(VEHICLE_ID);
        given(vehicleRepository.findByIdForUpdate(VEHICLE_ID)).willReturn(Optional.of(vehicle));
        given(auctionRepository.existsByVehicleId(VEHICLE_ID)).willReturn(true);

        assertThatThrownBy(() -> evaluationResultService.patch(
                command().mileage(NEW_MILEAGE).build()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(EvaluationErrorCode.RESULT_LOCKED_BY_AUCTION));

        then(vehicle).should(never()).reviseMileage(anyInt());
        then(vehicleImageService).shouldHaveNoInteractions();
        then(vehicleKeywordService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("수정에는 승인 알림이 다시 가지 않는다")
    void patchDoesNotNotify() {
        // given
        givenDiagnosedEvaluation();
        givenCurrentImages(IMAGE_1);
        givenCurrentKeywords();

        // when
        evaluationResultService.patch(command().mileage(NEW_MILEAGE).build());

        // then : 오타 하나 고친 것까지 "평가가 승인되었습니다"로 알리면 같은 문장이 쌓인다
        then(notificationPublisher).shouldHaveNoInteractions();
        then(evaluation).should(never()).approve();
    }

    @Test
    @DisplayName("없는 평가면 NOT_FOUND")
    void patchRejectsUnknownEvaluation() {
        // given
        given(evaluationRepository.findByIdForUpdate(EVALUATION_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> evaluationResultService.patch(
                command().mileage(NEW_MILEAGE).build()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(EvaluationErrorCode.NOT_FOUND));

        then(vehicleImageService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("세션에서 온 요청자가 자격 판정으로 그대로 넘어간다")
    void patchDelegatesAuthorization() {
        // given
        givenDiagnosedEvaluation();
        givenCurrentImages(IMAGE_1);
        givenCurrentKeywords();

        // when
        evaluationResultService.patch(command().mileage(NEW_MILEAGE).build());

        // then : 다른 값이 넘어가면 남의 담당 건을 고칠 수 있게 된다
        then(evaluation).should().validateDiagnosableBy(EVALUATOR_ID);
    }

    private void givenManagedDocument(String fileUrl) {
        given(fileStorage.isManagedUrl(fileUrl, FileCategory.DOCUMENT)).willReturn(true);
    }

    /** 이미 결과가 제출돼 네 칸이 채워진 평가 */
    private void givenDiagnosedEvaluation() {
        given(evaluationRepository.findByIdForUpdate(EVALUATION_ID))
                .willReturn(Optional.of(evaluation));
        given(evaluation.getVehicle()).willReturn(vehicle);
        given(vehicle.getId()).willReturn(VEHICLE_ID);
        given(vehicleRepository.findByIdForUpdate(VEHICLE_ID)).willReturn(Optional.of(vehicle));
        given(vehicle.isDiagnosed()).willReturn(true);
        given(evaluation.getStatus()).willReturn(EvaluationStatus.APPROVED);
        given(vehicle.getMileage()).willReturn(MILEAGE);
        given(vehicle.getEstimatedPrice()).willReturn(ESTIMATED_PRICE);
    }

    private void givenCurrentKeywords() {
        given(vehicleKeywordService.findByVehicle(vehicle)).willReturn(KEYWORDS);
    }

    /**
     * 사진을 보내지 않은 요청이 읽어 가는 현재 목록.
     * <p>
     * 목록을 먼저 다 만들고 나서 stubbing 을 시작한다. {@code given(...)} 인자 안에서 다른 목을
     * 다시 stubbing 하면 Mockito 가 바깥 stubbing 을 미완성으로 보고 던진다.
     */
    private void givenCurrentImages(String... imageUrls) {
        List<VehicleImage> images = Arrays.stream(imageUrls)
                .map(EvaluationResultPatchServiceTest::imageOf)
                .toList();

        given(vehicleImageRepository.findAllByVehicleOrderBySortOrderAsc(vehicle))
                .willReturn(images);
    }

    /** 사진을 보낸 요청이 교체 결과로 돌려받는 목록 */
    private void givenImagesReplacedWith(String... imageUrls) {
        given(vehicle.getId()).willReturn(VEHICLE_ID);
        given(vehicleImageService.register(any(VehicleImageRegisterCommand.class)))
                .willReturn(new VehicleImageRegisterInfo(VEHICLE_ID, numbered(imageUrls)));
    }

    private static List<VehicleImageRegisterInfo.RegisteredImage> numbered(String... imageUrls) {
        return IntStream.range(0, imageUrls.length)
                .mapToObj(index ->
                        new VehicleImageRegisterInfo.RegisteredImage(imageUrls[index], index + 1))
                .toList();
    }

    private static VehicleImage imageOf(String imageUrl) {
        VehicleImage image = mock(VehicleImage.class);
        given(image.getImageUrl()).willReturn(imageUrl);
        return image;
    }

    private static PatchCommandBuilder command() {
        return new PatchCommandBuilder();
    }

    /**
     * 항목별 수정은 <b>안 보낸 것</b>이 요점이라, 시나리오마다 null을 다섯 개씩 늘어놓으면 무엇을
     * 보낸 요청인지 읽히지 않는다. 채운 것만 적게 해서 그 요점이 코드에 드러나게 한다.
     */
    private static final class PatchCommandBuilder {

        private Integer mileage;
        private Long estimatedPrice;
        private List<String> imageUrls;
        private String diagnosticReportUrl;
        private List<VehicleKeyword> keywords;

        PatchCommandBuilder mileage(Integer mileage) {
            this.mileage = mileage;
            return this;
        }

        PatchCommandBuilder estimatedPrice(Long estimatedPrice) {
            this.estimatedPrice = estimatedPrice;
            return this;
        }

        PatchCommandBuilder imageUrls(List<String> imageUrls) {
            this.imageUrls = imageUrls;
            return this;
        }

        PatchCommandBuilder diagnosticReportUrl(String diagnosticReportUrl) {
            this.diagnosticReportUrl = diagnosticReportUrl;
            return this;
        }

        PatchCommandBuilder keywords(List<VehicleKeyword> keywords) {
            this.keywords = keywords;
            return this;
        }

        EvaluationResultPatchCommand build() {
            return new EvaluationResultPatchCommand(EVALUATION_ID, EVALUATOR_ID,
                    mileage, estimatedPrice, imageUrls, diagnosticReportUrl, keywords);
        }
    }
}
