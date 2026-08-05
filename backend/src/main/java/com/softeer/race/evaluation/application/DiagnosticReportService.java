package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.info.DiagnosticReportInfo;
import com.softeer.race.evaluation.domain.DiagnosticReportRepository;
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
 * 조회는 로그인만 확인한다. 진단서는 경매에 올라가면 입찰자 모두가 보는 자료라 열람을 좁힐 근거가
 * 약하고, 돌려주는 주소 자체가 공개라 좁혀도 실효가 없다.
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
    public DiagnosticReportInfo find(long evaluationId) {
        // 평가가 있는지는 확인한다. 인가가 아니라, 없는 평가와 진단서만 없는 평가를 화면이
        // 구분해 안내할 수 있어야 하기 때문이다
        if (!evaluationRepository.existsById(evaluationId)) {
            throw new BusinessException(EvaluationErrorCode.NOT_FOUND);
        }

        return diagnosticReportRepository.findByEvaluationId(evaluationId)
                .map(report -> DiagnosticReportInfo.from(evaluationId, report))
                .orElseThrow(() ->
                        new BusinessException(EvaluationErrorCode.DIAGNOSTIC_REPORT_NOT_FOUND));
    }
}
