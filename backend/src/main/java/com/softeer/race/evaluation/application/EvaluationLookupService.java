package com.softeer.race.evaluation.application;

import com.softeer.race.auction.domain.AuctionRepository;
import com.softeer.race.auction.domain.AuctionStatus;
import com.softeer.race.auction.domain.VehicleAuctionStatusRow;
import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.info.EvaluationDetailInfo;
import com.softeer.race.evaluation.application.dto.info.EvaluationSummaryInfo;
import com.softeer.race.evaluation.domain.AssignmentScope;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.vehicle.application.VehicleKeywordService;
import com.softeer.race.vehicle.domain.VehicleImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 방문견적 신청을 판매자와 평가사가 각자의 자리에서 조회한다.
 * <p>
 * 이 서비스가 있어야 <b>진단 완료가 판매자에게 도달한다.</b> 결과가 제출돼도 그 사실을 볼 창구가
 * 없으면 출품으로 넘어갈 수 없고, 흐름이 APPROVED에서 끊긴 채로 남는다.
 * <p>
 * 목록은 요청자 자신의 것만 돌려주므로 따로 인가하지 않는다 — 조회 조건이 곧 인가다. 상세만
 * 판매자와 배정 평가사로 좁힌다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationLookupService {

    private final EvaluationRepository evaluationRepository;
    private final AuctionRepository auctionRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final VehicleKeywordService vehicleKeywordService;

    /**
     * 판매자가 낸 신청들. 최신 접수부터.
     */
    public List<EvaluationSummaryInfo> findMyRequests(long sellerId) {
        return summaries(evaluationRepository.findBySellerId(sellerId));
    }

    /**
     * 평가사가 맡은 신청들. 범위가 무엇을 담고 어떤 순서로 나갈지까지 정한다.
     * <p>
     * 기본은 진행 중({@code ACTIVE})이다. 끝낸 진단이 목록에 남으면 새로 나갈 건이 그 아래
     * 묻히는 것이 이 구분을 만든 이유다. 완료된 건은 사라지지 않고 {@code COMPLETED}로 옮겨
     * 간다 — 승인은 경매 등록 전까지 다시 제출할 수 있어 열어 볼 길이 있어야 한다.
     */
    public List<EvaluationSummaryInfo> findMyAssignments(long evaluatorId, AssignmentScope scope) {
        return summaries(evaluationRepository.findByEvaluatorIdAndStatusIn(
                evaluatorId, scope.statuses(), scope.sort()));
    }

    /** 목록의 차량들에 최신 경매 상태를 조회 한 번으로 붙인다. */
    private List<EvaluationSummaryInfo> summaries(List<Evaluation> evaluations) {
        if (evaluations.isEmpty()) {
            return List.of();
        }

        List<Long> vehicleIds = evaluations.stream()
                .map(evaluation -> evaluation.getVehicle().getId())
                .distinct()
                .toList();
        Map<Long, AuctionStatus> statuses = auctionRepository
                .findLatestStatusesByVehicleIdIn(vehicleIds).stream()
                .collect(Collectors.toMap(
                        VehicleAuctionStatusRow::vehicleId,
                        VehicleAuctionStatusRow::status));

        return evaluations.stream()
                .map(evaluation -> EvaluationSummaryInfo.from(
                        evaluation, statuses.get(evaluation.getVehicle().getId())))
                .toList();
    }

    /**
     * 신청 한 건의 상세. 진단 전이면 결과 칸이 비어 나간다.
     * <p>
     * 사진과 키워드를 각자의 쿼리로 따로 읽는다. 평가 조회에 함께 조인하면 사진 장수 × 키워드
     * 개수만큼 행이 불어나 평가와 차량이 중복으로 실려 온다.
     */
    public EvaluationDetailInfo findDetail(long evaluationId, long userId) {
        Evaluation evaluation = evaluationRepository.findWithVehicleById(evaluationId)
                .orElseThrow(() -> new BusinessException(EvaluationErrorCode.NOT_FOUND));

        // 권한 없음을 403이 아니라 404로 낸다. 구분되면 id를 훑어 남의 신청이 있는지 알아낼 수 있고,
        // 여기에는 방문 주소가 들어 있어 그 노출이 실제 피해가 된다
        if (!evaluation.isViewableBy(userId)) {
            throw new BusinessException(EvaluationErrorCode.NOT_FOUND);
        }

        return EvaluationDetailInfo.of(
                evaluation,
                vehicleImageRepository.findAllByVehicleOrderBySortOrderAsc(evaluation.getVehicle()),
                vehicleKeywordService.findByVehicle(evaluation.getVehicle()));
    }
}
