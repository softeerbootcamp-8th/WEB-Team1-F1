package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.evaluation.presentation.response.DiagnosticReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "DiagnosticReport", description = "진단서 API")
public interface DiagnosticReportApi {

    @Operation(summary = "진단서 조회",
            description = """
                    평가에 붙은 진단서 주소를 돌려줍니다. 현재는 로그인만 확인합니다.

                    진단서를 붙이는 것은 이 API가 아니라 평가 결과 제출
                    (PUT /api/evaluations/{evaluationId}/result)입니다. 진단서는 주행거리 · 시세 ·
                    차량 사진과 함께 제출되며 따로 붙일 수 없습니다.

                    돌려주는 fileUrl 은 만료되지 않는 공개 주소입니다. 진단서는 경매에 올라가면
                    입찰자 모두가 보는 자료라 주소 자체를 감추지 않습니다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회되었습니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "404",
            description = "없는 평가이거나, 아직 결과가 제출되지 않은 경우입니다. 두 경우는 서로 다른 "
                    + "코드로 구분됩니다.")
    ResponseEntity<DiagnosticReportResponse> find(
            AuthenticatedUser authenticatedUser, long evaluationId);
}
