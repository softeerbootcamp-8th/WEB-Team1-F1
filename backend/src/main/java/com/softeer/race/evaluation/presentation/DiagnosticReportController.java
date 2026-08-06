package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.auth.presentation.annotation.LoginUser;
import com.softeer.race.evaluation.application.DiagnosticReportService;
import com.softeer.race.evaluation.presentation.response.DiagnosticReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluations/{evaluationId}/diagnostic-report")
@RequiredArgsConstructor
public class DiagnosticReportController implements DiagnosticReportApi {

    private final DiagnosticReportService diagnosticReportService;

    /**
     * 조회만 있다. 붙이는 것은 평가 결과 제출({@code PUT /api/evaluations/{id}/result})이 한다.
     */
    @Override
    @GetMapping
    public ResponseEntity<DiagnosticReportResponse> find(
            @LoginUser AuthenticatedUser authenticatedUser,
            @PathVariable long evaluationId) {

        return ResponseEntity.ok(DiagnosticReportResponse.from(
                diagnosticReportService.find(evaluationId, authenticatedUser.id())));
    }
}
