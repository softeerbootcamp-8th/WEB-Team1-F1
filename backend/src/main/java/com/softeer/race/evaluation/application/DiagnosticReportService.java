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
 * <b>첨부는 이 신청에 배정된 평가사만 할 수 있다.</b> 배정을 자격의 증명으로 쓰고 역할을 따로
 * 묻지 않는다 — 배정은 대기 목록에서 수락해야 받는다({@code EvaluationAssignmentService}).
 * 다만 그 수락이 아직 역할을 검사하지 않아, <b>지금은 평가사가 아닌 회원도 수락을 거쳐 배정되면
 * 진단서를 붙일 수 있다.</b> 그 구멍은 여기가 아니라 배정하는 곳에서 막아야 한다 — 여기에 역할
 * 검사를 더하면 같은 규칙이 두 곳에 생겨 한쪽만 고쳐지는 날 어긋난다.
 * <p>
 * <b>조회는 여전히 로그인만 확인한다.</b> 진단서는 경매에 올라가면 입찰자 모두가 보는 자료라
 * 열람을 좁힐 근거가 약하고, 주소 자체가 공개라 좁혀도 실효가 없다.
 * <p>
 * TODO 인가가 들어오면 조회를 신청한 판매자와 배정된 평가사로 좁힐지 정한다.
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
     *
     * @param evaluatorId 요청자. 이 신청에 배정된 평가사여야 한다.
     *                    세션에서만 나와야 한다 — 본문으로 받으면 남의 이름을 대고 올릴 수 있다
     */
    @Transactional
    public DiagnosticReportInfo attach(long evaluationId, long evaluatorId, String fileUrl) {
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

        // 상태와 담당자 판정은 엔티티가 한다. 규칙을 어긴 상태가 애초에 만들어지지 않아야
        // 다른 호출자가 생겨도 검증이 빠지지 않는다
        evaluation.validateDiagnosableBy(evaluatorId);

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
