package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.evaluation.application.DiagnosticReportService;
import com.softeer.race.evaluation.presentation.request.DiagnosticReportAttachRequest;
import com.softeer.race.evaluation.presentation.response.DiagnosticReportResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluations/{evaluationId}/diagnostic-report")
@RequiredArgsConstructor
public class DiagnosticReportController implements DiagnosticReportApi {

    private final DiagnosticReportService diagnosticReportService;

    /**
     * PUT이고 201이 아니라 200이다. 재첨부를 교체로 처리하므로 이 요청은 "이 평가의 진단서는
     * 이것"이라는 대체이고, 같은 요청을 몇 번 보내도 결과가 같다. 그래서 매번 새 자원이 생기는
     * POST · 201이 맞지 않는다.
     * <p>
     * {@code authenticatedUser}를 쓰지 않지만 파라미터로 받는다. 인터셉터가 이 파라미터를 보고
     * 인증을 요구하므로, <b>지우면 인증이 함께 사라진다.</b>
     * <p>
     * TODO 인가가 들어오면 이 평가에 배정된 평가사로 좁힌다. 지금은 로그인만 확인한다.
     */
    @Override
    @PutMapping
    public ResponseEntity<DiagnosticReportResponse> attach(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable long evaluationId,
            @Valid @RequestBody DiagnosticReportAttachRequest request) {

        return ResponseEntity.ok(DiagnosticReportResponse.from(
                diagnosticReportService.attach(evaluationId, request.fileUrl())));
    }

    /**
     * TODO 인가가 들어오면 신청한 판매자와 배정된 평가사로 좁힌다. 지금은 로그인만 확인한다.
     */
    @Override
    @GetMapping
    public ResponseEntity<DiagnosticReportResponse> find(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable long evaluationId) {

        return ResponseEntity.ok(
                DiagnosticReportResponse.from(diagnosticReportService.find(evaluationId)));
    }
}
