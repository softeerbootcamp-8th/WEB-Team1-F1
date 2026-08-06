package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.evaluation.presentation.response.EvaluationDetailResponse;
import com.softeer.race.evaluation.presentation.response.EvaluationSummariesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "EvaluationLookup", description = "방문견적 신청 조회 API")
public interface EvaluationLookupApi {

    @Operation(summary = "내가 낸 신청 목록",
            description = """
                    판매자로서 낸 방문견적 신청들을 최신 접수부터 돌려줍니다.

                    status가 APPROVED면 평가사의 진단이 끝난 것이고, 그때부터 그 차량을 경매로
                    출품할 수 있습니다. 진단 결과(주행거리 · 시세 · 사진 · 진단서)는 이 목록에
                    없으며 상세 조회에서 받습니다.
                    """)
    @ApiResponse(responseCode = "200", description = "없으면 빈 배열입니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    ResponseEntity<EvaluationSummariesResponse> findMyRequests(AuthenticatedUser authenticatedUser);

    @Operation(summary = "내가 맡은 신청 목록",
            description = """
                    평가사로서 수락한 방문견적 신청들을 방문일이 임박한 순으로 돌려줍니다.

                    아직 수락하지 않은 신청은 배정 대기 목록(GET /api/evaluations/assignable)에
                    있습니다. 판매자 연락처는 수락할 때 받은 응답에 있고 이 목록에는 없습니다.
                    """)
    @ApiResponse(responseCode = "200", description = "없으면 빈 배열입니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    ResponseEntity<EvaluationSummariesResponse> findMyAssignments(AuthenticatedUser authenticatedUser);

    @Operation(summary = "신청 상세",
            description = """
                    신청 한 건의 전부를 돌려줍니다. 진단이 끝났으면 평가사가 제출한 주행거리 ·
                    예상 시세 · 차량 사진 · 진단서 주소가 함께 나갑니다.

                    아직 진단 전이면 그 값들이 null이고 imageUrls에는 카탈로그 이미지만 있습니다.

                    **신청한 판매자와 배정된 평가사만 조회할 수 있습니다.** 방문 주소가 들어 있어
                    진단서 조회처럼 열어 둘 수 없습니다.
                    """)
    @ApiResponse(responseCode = "200", description = "조회되었습니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "404",
            description = "없는 신청이거나 조회 권한이 없는 경우입니다. 둘을 구분하지 않는 것은 "
                    + "id를 훑어 남의 신청과 그 방문 주소를 알아내지 못하게 하기 위해서입니다.")
    ResponseEntity<EvaluationDetailResponse> findDetail(
            AuthenticatedUser authenticatedUser, long evaluationId);
}
