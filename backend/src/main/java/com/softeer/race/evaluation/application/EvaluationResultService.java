package com.softeer.race.evaluation.application;

import static com.softeer.race.notification.domain.NotificationType.EVAL_APPROVED;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.command.EvaluationResultSubmitCommand;
import com.softeer.race.evaluation.application.dto.info.EvaluationResultInfo;
import com.softeer.race.evaluation.domain.DiagnosticReport;
import com.softeer.race.evaluation.domain.DiagnosticReportRepository;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.notification.application.NotificationPublisher;
import com.softeer.race.storage.domain.FileCategory;
import com.softeer.race.storage.domain.FileStorage;
import com.softeer.race.vehicle.application.VehicleImageService;
import com.softeer.race.vehicle.application.dto.command.VehicleImageRegisterCommand;
import com.softeer.race.vehicle.application.dto.info.VehicleImageRegisterInfo;
import com.softeer.race.vehicle.domain.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 평가사가 방문해 확인한 결과를 제출한다. 실측 주행거리 · 산정 시세 · 차량 사진 · 진단서가
 * <b>한 트랜잭션에서 함께</b> 반영된다.
 * <p>
 * 조각난 API로 나누지 않는 이유는 {@code Vehicle}이 걸어 둔 불변식 때문이다 — "경매가 붙은 차량은
 * 주행거리가 채워져 있다"에 경매 목록과 경매방이 기대고 있는데, 사진만 올리고 주행거리를 빼먹을
 * 수 있으면 그 불변식을 지키는 사람이 아무도 없다. 여기서 함께 받으면 반쪽짜리 차량이 나올 수 없다.
 * <p>
 * 인가도 묶은 덕에 성립한다. 사진 등록은 {@code vehicleId}만 받아 "배정된 평가사인가"를 물을 수
 * 없지만, 이 유스케이스는 {@code evaluationId} 축이라 그 질문이 가능하다.
 * <p>
 * <b>평가 행을 잠그고 시작한다.</b> 담당자 한 명만 호출하는 흐름이라 경합이 없어 보이지만, 같은
 * 평가사가 버튼을 두 번 누르거나 탭 두 개에서 보내면 두 요청이 겹친다. 그러면 둘 다 "진단서 없음"을
 * 읽고 둘 다 insert 해서 {@code evaluation_id} unique 제약에 걸린다 — 배정이 {@code findByIdForUpdate}로
 * 막는 것과 같은 종류의 경합이고, 여기서는 상대가 남이 아니라 자기 자신이다.
 * <p>
 * <b>승인 알림도 여기서 발행한다.</b> 받을 사람이 판매자 하나뿐이라 고를 것이 없다 —
 * 반려 알림이 붙어 갈래가 생기면 그때 {@code AuctionEndNotifier}처럼 뗀다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationResultService {

    private final EvaluationRepository evaluationRepository;
    private final DiagnosticReportRepository diagnosticReportRepository;
    private final VehicleImageService vehicleImageService;
    private final FileStorage fileStorage;
    private final NotificationPublisher notificationPublisher;

    /**
     * 방문 결과를 제출한다. 이미 제출된 결과가 있으면 통째로 갈아 끼운다.
     */
    @Transactional
    public EvaluationResultInfo submit(EvaluationResultSubmitCommand command) {
        // 주소 검증을 가장 먼저 한다. 거부될 요청에 조회를 태우지 않고,
        // 사진이 지워진 뒤에 진단서 주소가 잘못된 것을 발견하는 순서가 되지 않게 한다
        validateManagedDocument(command.diagnosticReportUrl());

        // 잠그고 읽는다. 진단서 조회와 저장 사이에 창이 있어, 잠금 없이 두면 동시에 들어온 두
        // 제출이 모두 "진단서 없음"을 읽고 둘 다 insert 한다. evaluation_id 가 unique 라
        // 뒤엣것이 제약 위반으로 500 이 된다 — 같은 평가사가 버튼을 두 번 누르면 성립한다
        Evaluation evaluation = evaluationRepository.findByIdForUpdate(command.evaluationId())
                .orElseThrow(() -> new BusinessException(EvaluationErrorCode.NOT_FOUND));

        evaluation.validateDiagnosableBy(command.evaluatorId());

        Vehicle vehicle = evaluation.getVehicle();
        // 요청 검증이 최소 한 장을 강제하므로 첫 장이 항상 있다
        vehicle.completeDiagnosis(command.mileage(), command.estimatedPrice(), command.imageUrls().getFirst());

        // 사진 교체는 기존 서비스를 부른다. 이미 열린 트랜잭션에 참여하므로 한 단위로 묶이고,
        // 사진 주소가 이미지인지 확인하는 것도 그쪽이 한다 — 같은 판정을 두 곳에 두지 않는다
        VehicleImageRegisterInfo images = vehicleImageService.register(
                new VehicleImageRegisterCommand(vehicle.getId(), command.imageUrls()));

        DiagnosticReport report = attachOrReplace(evaluation, command);

        evaluation.approve();

        // 이 트랜잭션 안에서 발행한다. 제출이 롤백됐는데 승인 알림만 남으면 안 된다.
        // 판매자 식별자는 프록시가 이미 들고 있어 회원 조회가 늘지 않는다
        notificationPublisher.publish(vehicle.getSeller().getId(), EVAL_APPROVED, command.evaluationId());

        // 제출 시각을 응답에 싣는다. 교체는 더티 체킹이라 커밋 시점에야 flush 되고,
        // 그 전까지 updatedAt은 이전 값이라 방금 올린 결과에 예전 시각이 붙어 나간다
        evaluationRepository.flush();

        // 평가 id를 엔티티에서 다시 꺼내지 않는다. 커맨드가 이미 들고 있는 값이다
        return new EvaluationResultInfo(
                command.evaluationId(),
                vehicle.getId(),
                evaluation.getStatus().name(),
                command.mileage(),
                command.estimatedPrice(),
                images.images().stream()
                        .map(VehicleImageRegisterInfo.RegisteredImage::imageUrl)
                        .toList(),
                report.getFileUrl(),
                evaluation.getUpdatedAt());
    }

    private void validateManagedDocument(String fileUrl) {
        // 종류를 DOCUMENT로 못 박는다. "우리가 발급한 주소인가"만 물으면 차량 사진 JPEG도 통과해
        // 진단서 자리에 사진이 박힌다
        if (!fileStorage.isManagedUrl(fileUrl, FileCategory.DOCUMENT)) {
            throw new BusinessException(EvaluationErrorCode.UNMANAGED_DOCUMENT_URL);
        }
    }

    private DiagnosticReport attachOrReplace(Evaluation evaluation,
                                             EvaluationResultSubmitCommand command) {
        return diagnosticReportRepository.findByEvaluationId(command.evaluationId())
                .map(existing -> {
                    existing.replaceFile(command.diagnosticReportUrl());
                    return existing;
                })
                .orElseGet(() -> diagnosticReportRepository.save(
                        DiagnosticReport.attach(evaluation, command.diagnosticReportUrl())));
    }
}
