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
                    평가에 붙은 진단서 주소를 돌려줍니다. **신청한 판매자와 배정된 평가사만
                    조회할 수 있습니다.**

                    진단서를 붙이는 것은 이 API가 아니라 평가 결과 제출
                    (PUT /api/evaluations/{evaluationId}/result)입니다. 진단서는 주행거리 · 시세 ·
                    차량 사진과 함께 제출되며 따로 붙일 수 없습니다.

                    돌려주는 fileUrl 은 만료되지 않는 공개 주소입니다. 주소를 받아 간 뒤에는
                    회수할 수 없으므로, 열람 대상을 좁히는 것은 그 주소를 누구에게 건네는지까지입니다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회되었습니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "404",
            description = "없는 평가이거나, 조회 권한이 없거나, 아직 결과가 제출되지 않은 경우입니다. "
                    + "앞의 둘은 같은 코드로 나가며(id를 훑어 남의 진단서를 알아내지 못하게), "
                    + "미제출만 다른 코드로 구분됩니다.")
    ResponseEntity<DiagnosticReportResponse> find(
            AuthenticatedUser authenticatedUser, long evaluationId);
}
