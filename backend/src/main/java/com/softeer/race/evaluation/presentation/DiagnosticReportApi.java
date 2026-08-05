package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.evaluation.presentation.request.DiagnosticReportAttachRequest;
import com.softeer.race.evaluation.presentation.response.DiagnosticReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "DiagnosticReport", description = "진단서 API")
public interface DiagnosticReportApi {

    @Operation(summary = "진단서 첨부",
            description = """
                    평가사가 진단서 PDF를 평가에 붙입니다. 파일은 이 API로 보내지 않습니다.

                    1. 업로드 주소 발급 API(POST /api/uploads/presigned)에 application/pdf 로 요청합니다.
                    2. 받은 uploadUrl 로 파일을 PUT 합니다.
                    3. 함께 받은 fileUrl 을 이 API로 보냅니다.

                    POST가 아니라 PUT인 것은 재첨부를 교체로 처리하기 때문입니다. 같은 평가에 다시
                    보내면 진단서가 갈아 끼워지고, 몇 번을 보내도 결과는 마지막에 보낸 파일 하나입니다.

                    **이 신청에 배정된 평가사만 첨부할 수 있습니다.** 배정은 배정 대기 목록에서
                    수락할 때 받으며, 첨부한다고 담당이 정해지지는 않습니다. 아직 아무도 수락하지
                    않은 신청에는 누구도 붙일 수 없습니다.
                    """)
    @ApiResponse(responseCode = "200", description = "붙었거나 갈아 끼워졌습니다.")
    @ApiResponse(responseCode = "400",
            description = "우리가 발급하지 않았거나 문서가 아닌 주소입니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "403", description = "다른 평가사가 담당인 신청입니다.")
    @ApiResponse(responseCode = "404", description = "없는 평가입니다.")
    @ApiResponse(responseCode = "409",
            description = "이미 반려되어 끝났거나, 아직 담당 평가사가 정해지지 않은 신청입니다. "
                    + "뒤쪽은 배정 대기 목록에서 수락하면 풀립니다.")
    ResponseEntity<DiagnosticReportResponse> attach(
            AuthenticatedUser authenticatedUser,
            long evaluationId,
            DiagnosticReportAttachRequest request);

    @Operation(summary = "진단서 조회",
            description = """
                    평가에 붙은 진단서 주소를 돌려줍니다. 현재는 로그인만 확인하며, 열람 대상을
                    좁히는 것은 인가가 도입될 때 함께 들어옵니다.

                    돌려주는 fileUrl 은 만료되지 않는 공개 주소입니다. 진단서는 경매에 올라가면
                    입찰자 모두가 보는 자료라 주소 자체를 감추지 않습니다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회되었습니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "404",
            description = "없는 평가이거나, 아직 진단서가 붙지 않은 경우입니다. 두 경우는 서로 다른 "
                    + "코드로 구분됩니다.")
    ResponseEntity<DiagnosticReportResponse> find(
            AuthenticatedUser authenticatedUser, long evaluationId);
}
