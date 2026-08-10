package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.info.EvaluationDetailInfo;
import com.softeer.race.evaluation.application.dto.info.EvaluationSummaryInfo;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.vehicle.application.VehicleKeywordService;
import com.softeer.race.vehicle.domain.VehicleImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    private final VehicleImageRepository vehicleImageRepository;
    private final VehicleKeywordService vehicleKeywordService;

    /**
     * 판매자가 낸 신청들. 최신 접수부터.
     */
    public List<EvaluationSummaryInfo> findMyRequests(long sellerId) {
        return evaluationRepository.findBySellerId(sellerId).stream()
                .map(EvaluationSummaryInfo::from)
                .toList();
    }

    /**
     * 평가사가 맡은 신청들. 방문일이 임박한 순으로.
     */
    public List<EvaluationSummaryInfo> findMyAssignments(long evaluatorId) {
        return evaluationRepository.findByEvaluatorId(evaluatorId).stream()
                .map(EvaluationSummaryInfo::from)
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
