package com.softeer.race.evaluation.application;

import static com.softeer.race.notification.domain.NotificationType.EVAL_APPROVED;
import static com.softeer.race.notification.domain.NotificationType.EVAL_REJECTED;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.command.EvaluationRejectCommand;
import com.softeer.race.evaluation.application.dto.command.EvaluationResultSubmitCommand;
import com.softeer.race.evaluation.application.dto.info.EvaluationRejectionInfo;
import com.softeer.race.evaluation.application.dto.info.EvaluationResultInfo;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.notification.application.NotificationPublisher;
import com.softeer.race.storage.domain.FileCategory;
import com.softeer.race.storage.domain.FileStorage;
import com.softeer.race.vehicle.application.VehicleImageService;
import com.softeer.race.vehicle.application.VehicleKeywordService;
import com.softeer.race.vehicle.application.dto.command.VehicleImageRegisterCommand;
import com.softeer.race.vehicle.application.dto.info.VehicleImageRegisterInfo;
import com.softeer.race.vehicle.domain.Vehicle;
import com.softeer.race.vehicle.domain.VehicleKeyword;
import java.util.List;
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
 * <b>평가 행을 잠그고 시작한다.</b> 같은 평가사가 두 번 보내면 사진 교체가 겹쳐 두 벌이 남는다.
 * <p>
 * <b>승인과 반려를 한 서비스가 맡는다.</b> 방문 결과라는 한 유스케이스의 두 판정이라, 나누면
 * "배정된 평가사만 결과를 낸다"는 같은 규칙이 두 서비스에 생긴다. 트랜잭션 경계와 잠금 방식도 같다.
 * <p>
 * <b>알림도 각 판정이 직접 발행한다.</b> 받을 사람이 판매자 하나뿐이라 고를 것이 없다. 두 판정이
 * 한 메서드의 분기가 아니라 각자의 메서드라, 알림도 갈래 없이 한 줄씩이다 — 받는 사람을 골라야
 * 하는 날이 오면 그때 {@code AuctionEndNotifier}처럼 뗀다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationResultService {

    private final EvaluationRepository evaluationRepository;
    private final VehicleImageService vehicleImageService;
    private final VehicleKeywordService vehicleKeywordService;
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

        // 잠그고 읽는다, 사진 교체가 전부 지우고 다시 넣는 방식이라 겹치면 두 벌이 남는다
        Evaluation evaluation = evaluationRepository.findByIdForUpdate(command.evaluationId())
                .orElseThrow(() -> new BusinessException(EvaluationErrorCode.NOT_FOUND));

        evaluation.validateDiagnosableBy(command.evaluatorId());

        Vehicle vehicle = evaluation.getVehicle();
        // 요청 검증이 최소 한 장을 강제하므로 첫 장이 항상 있다
        vehicle.completeDiagnosis(command.mileage(), command.estimatedPrice(),
                command.imageUrls().getFirst(), command.diagnosticReportUrl());

        // 사진 교체는 기존 서비스를 부른다. 이미 열린 트랜잭션에 참여하므로 한 단위로 묶이고,
        // 사진 주소가 이미지인지 확인하는 것도 그쪽이 한다 — 같은 판정을 두 곳에 두지 않는다
        VehicleImageRegisterInfo images = vehicleImageService.register(
                new VehicleImageRegisterCommand(vehicle.getId(), command.imageUrls()));

        // 키워드도 사진과 같이 통째로 교체한다. 재제출이 결과를 갈아 끼우는 것이므로 앞서 매긴
        // 키워드가 남으면 평가사가 뺀 키워드가 그대로 붙어 있게 된다.
        // 저장된 목록을 돌려받아 응답에 쓴다 — 중복 제거와 정렬이 거기서 끝나 있다
        List<VehicleKeyword> keywords = vehicleKeywordService.replace(vehicle, command.keywords());

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
                vehicle.getDiagnosticReportUrl(),
                evaluation.getUpdatedAt(),
                keywords);
    }

    /**
     * 방문 결과를 반려로 끝낸다. 사유가 함께 저장되고 판매자에게 알림이 간다.
     * <p>
     * {@link #submit}과 달리 차량을 건드리지 않는다. 반려된 신청의 차량은 진단 전 상태 그대로
     * 남아, 판매자가 같은 번호판으로 다시 신청할 수 있다({@code EvaluationStatus.inProgress}가
     * REJECTED를 빼고 있어 중복 접수 차단에 걸리지 않는다).
     * <p>
     * <b>여기서도 평가 행을 잠그고 시작한다.</b> 사진 교체 때문이 아니라, 승인 제출과 반려가
     * 동시에 들어오면 둘 다 REQUESTED를 읽고 통과한 뒤 나중 쓰기가 앞의 판정을 덮기 때문이다.
     * 승인이 이겨 버리면 판매자는 반려 알림을 받아 놓고 상태는 APPROVED인 신청을 보게 된다.
     */
    @Transactional
    public EvaluationRejectionInfo reject(EvaluationRejectCommand command) {
        Evaluation evaluation = evaluationRepository.findByIdForUpdate(command.evaluationId())
                .orElseThrow(() -> new BusinessException(EvaluationErrorCode.NOT_FOUND));

        evaluation.validateRejectableBy(command.evaluatorId());
        evaluation.reject(command.reason());

        // submit과 같은 이유로 이 트랜잭션 안에서 발행한다. 반려가 롤백됐는데 알림만 남으면 안 된다.
        // findByIdForUpdate가 join fetch 없이 읽으므로 vehicle 프록시 초기화 쿼리가 한 번 나간다 —
        // 잠금 범위를 평가 한 행으로 제한한 대가이고, 트랜잭션 안이라 지연 로딩이 성립한다
        notificationPublisher.publish(
                evaluation.getVehicle().getSeller().getId(), EVAL_REJECTED, command.evaluationId());

        // 반려 시각을 응답에 싣는다. 더티 체킹이라 flush 전까지 updatedAt은 배정 시각 그대로다
        evaluationRepository.flush();

        return EvaluationRejectionInfo.from(evaluation);
    }

    private void validateManagedDocument(String fileUrl) {
        // 종류를 DOCUMENT로 못 박는다. "우리가 발급한 주소인가"만 물으면 차량 사진 JPEG도 통과해
        // 진단서 자리에 사진이 박힌다
        if (!fileStorage.isManagedUrl(fileUrl, FileCategory.DOCUMENT)) {
            throw new BusinessException(EvaluationErrorCode.UNMANAGED_DOCUMENT_URL);
        }
    }
}
