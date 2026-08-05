package com.softeer.race.evaluation.application;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.evaluation.application.dto.info.DiagnosticReportInfo;
import com.softeer.race.evaluation.domain.DiagnosticReport;
import com.softeer.race.evaluation.domain.DiagnosticReportRepository;
import com.softeer.race.evaluation.domain.Evaluation;
import com.softeer.race.evaluation.domain.EvaluationRepository;
import com.softeer.race.evaluation.exception.EvaluationErrorCode;
import com.softeer.race.storage.domain.FileCategory;
import com.softeer.race.storage.domain.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 평가사가 남긴 진단서를 평가에 붙이고 조회한다.
 * <p>
 * 파일은 이 서비스를 통과하지 않는다. 클라이언트가 발급받은 주소로 저장소에 직접 올린 뒤 그
 * 주소만 보내고, 서버는 <b>그 주소가 우리가 발급한 문서 주소인지</b> 확인해 평가에 이어 붙인다.
 * <p>
 * <b>인가를 하지 않는다.</b> 로그인한 사용자면 누구나 붙이고 볼 수 있다. 평가사 배정이 이미 있어
 * ({@code EvaluationAssignmentService}) "배정된 담당자인가"를 물을 수는 있지만, 그 서비스 자신도
 * 역할을 검사하지 않아 아무나 배정될 수 있는 상태다. 배정을 근거로 삼으면 검사하지 않은 값 위에
 * 검사를 쌓는 셈이라, 인가는 두 곳에 한꺼번에 들어오는 편이 맞다.
 * <p>
 * TODO 인가가 들어오면 첨부는 배정된 평가사로, 조회는 신청한 판매자와 배정된 평가사로 좁힌다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagnosticReportService {

    private final EvaluationRepository evaluationRepository;
    private final DiagnosticReportRepository diagnosticReportRepository;
    private final FileStorage fileStorage;

    /**
     * 평가에 진단서를 붙인다. 이미 붙어 있으면 갈아 끼운다.
     */
    @Transactional
    public DiagnosticReportInfo attach(long evaluationId, String fileUrl) {
        // 주소 검증을 가장 먼저 한다. 거부될 요청에 조회를 태울 이유가 없다.
        //
        // 종류를 DOCUMENT로 못 박는다. "우리가 발급한 주소인가"만 물으면 차량 사진 JPEG도 통과해
        // 진단서 자리에 사진이 박힌다 — 차량 사진 쪽에서 PDF를 막는 것과 같은 판정이 반대 방향으로
        // 필요하다
        if (!fileStorage.isManagedUrl(fileUrl, FileCategory.DOCUMENT)) {
            throw new BusinessException(EvaluationErrorCode.UNMANAGED_DOCUMENT_URL);
        }

        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new BusinessException(EvaluationErrorCode.NOT_FOUND));

        // 반려 여부는 엔티티가 본다. 규칙을 어긴 상태가 애초에 만들어지지 않아야
        // 다른 호출자가 생겨도 검증이 빠지지 않는다
        evaluation.validateDiagnosable();

        DiagnosticReport report = diagnosticReportRepository.findByEvaluationId(evaluationId)
                .map(existing -> {
                    existing.replaceFile(fileUrl);
                    return existing;
                })
                .orElseGet(() -> diagnosticReportRepository.save(
                        DiagnosticReport.attach(evaluation, fileUrl)));

        // 교체는 더티 체킹이라 커밋 시점에야 flush 되고, 그 전까지 updatedAt은 이전 값이다.
        // 응답에 첨부 시각을 싣기로 한 이상 여기서 감사 필드를 확정해야 한다 —
        // 그러지 않으면 방금 올린 파일에 예전 시각이 붙어 나간다
        diagnosticReportRepository.flush();

        return DiagnosticReportInfo.from(evaluationId, report);
    }

    /**
     * 평가에 붙은 진단서를 조회한다.
     * <p>
     * 돌려주는 {@code fileUrl}은 만료되지 않는 공개 주소다. 진단서는 경매에 올라가면 입찰자
     * 모두가 보는 자료라 주소 자체를 감추지 않는다.
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
