package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.info.DiagnosticReportInfo;
import com.softeer.race.evaluation.domain.DiagnosticReportRepository;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 평가에 붙은 진단서를 조회한다.
 * <p>
 * <b>붙이는 일은 여기서 하지 않는다.</b> 진단서는 주행거리 · 시세 · 사진과 함께 한 번에 제출되고
 * ({@code EvaluationResultService}), 입구를 둘로 두면 따로 붙인 진단서와 제출에 실려 온 진단서 중
 * 무엇이 이기는지를 매번 정해야 한다.
 * <p>
 * <b>신청한 판매자와 배정된 평가사만 조회한다.</b> 돌려주는 주소가 공개라 이 검사가 기밀을
 * 보장하지는 못한다 — 한 번 받아 간 사람은 계속 열 수 있다. 그래도 두는 이유는 <b>출품 전에는
 * 아직 공개된 자료가 아니기 때문</b>이다. 경매에 올라가면 입찰자 모두가 보게 되지만 그 전까지는
 * 판매자와 담당 평가사의 것이고, 로그인한 아무나 id를 훑어 남의 진단서 주소를 긁어모을 수 있으면
 * 그 주소는 회수할 수 없다.
 * <p>
 * 입찰자에게 진단서를 보여주는 일은 이 API가 하지 않는다. 입찰자가 아는 것은 {@code auctionId}뿐이라
 * {@code evaluationId}로 키가 걸린 여기를 호출할 방법이 없고, 경매방 응답이 주소를 담는 형태가 된다.
 * <p>
 * TODO 평가 결과 상세 조회가 들어오면 이 API를 그쪽으로 흡수할지 정한다.
 *      진단서만 따로 볼 화면이 없으면 남겨 둘 이유가 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagnosticReportService {

    private final EvaluationRepository evaluationRepository;
    private final DiagnosticReportRepository diagnosticReportRepository;

    /**
     * 평가에 붙은 진단서를 조회한다.
     * <p>
     * 돌려주는 {@code fileUrl}은 만료되지 않는 공개 주소다.
     */
    public DiagnosticReportInfo find(long evaluationId, long userId) {
        // existsById가 아니라 엔티티를 읽는다. 열람 판정에 판매자와 담당 평가사가 필요하고,
        // 이 쿼리가 vehicle과 evaluator를 함께 붙여 와 지연 로딩이 더 나가지 않는다
        Evaluation evaluation = evaluationRepository.findWithVehicleById(evaluationId)
                .orElseThrow(() -> new BusinessException(EvaluationErrorCode.NOT_FOUND));

        // 권한 없음을 403이 아니라 404로 낸다. 구분되면 id를 훑어 어느 평가에 진단서가 붙어
        // 있는지 알아낼 수 있다
        if (!evaluation.isViewableBy(userId)) {
            throw new BusinessException(EvaluationErrorCode.NOT_FOUND);
        }

        return diagnosticReportRepository.findByEvaluationId(evaluationId)
                .map(report -> DiagnosticReportInfo.from(evaluationId, report))
                .orElseThrow(() ->
                        new BusinessException(EvaluationErrorCode.DIAGNOSTIC_REPORT_NOT_FOUND));
    }
}
