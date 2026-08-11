package com.softeer.race.evaluation.presentation;

import com.softeer.race.auth.domain.AuthenticatedUser;
import com.softeer.race.evaluation.presentation.request.EvaluationRejectRequest;
import com.softeer.race.evaluation.presentation.request.EvaluationResultPatchRequest;
import com.softeer.race.evaluation.presentation.request.EvaluationResultSubmitRequest;
import com.softeer.race.evaluation.presentation.response.EvaluationRejectionResponse;
import com.softeer.race.evaluation.presentation.response.EvaluationResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "EvaluationResult", description = "평가 결과 API")
public interface EvaluationResultApi {

    @Operation(summary = "평가 결과 제출",
            description = """
                    평가사가 방문해 확인한 결과를 제출합니다. 실측 주행거리 · 산정 시세 · 차량 사진 ·
                    진단서가 한 번에 반영되며, 하나라도 잘못되면 아무것도 반영되지 않습니다.

                    사진과 진단서는 이 API로 보내지 않습니다. 업로드 주소 발급 API
                    (POST /api/uploads/presigned)로 각각 주소를 받아 파일을 올린 뒤, 돌려받은
                    fileUrl 을 여기로 보냅니다. 사진은 이미지 형식으로, 진단서는 application/pdf 로
                    발급받아야 합니다.

                    사진 목록은 통째로 교체됩니다. 한 장을 더하려면 기존 목록에 그 한 장을 더해
                    전부 보내야 합니다.

                    POST가 아니라 PUT인 것은 재제출을 교체로 처리하기 때문입니다. 잘못 적은
                    주행거리를 고치려면 다시 제출하면 되고, 몇 번을 보내도 결과는 마지막에 보낸
                    내용 하나입니다.

                    **이 신청에 배정된 평가사만 제출할 수 있습니다.** 배정은 배정 대기 목록에서
                    수락할 때 받으며, 아직 아무도 수락하지 않은 신청에는 누구도 제출할 수 없습니다.

                    제출이 끝나면 신청 상태가 APPROVED가 되고, 그때부터 차량에 주행거리와
                    예상 시세가 채워집니다.
                    """)
    @ApiResponse(responseCode = "200", description = "제출되었거나 갈아 끼워졌습니다.")
    @ApiResponse(responseCode = "400",
            description = "값이 허용 범위를 벗어났거나, 우리가 발급하지 않은 주소입니다. "
                    + "사진 자리에 문서 주소를, 진단서 자리에 이미지 주소를 보낸 경우도 포함합니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "403", description = "다른 평가사가 담당인 신청입니다.")
    @ApiResponse(responseCode = "404", description = "없는 평가입니다.")
    @ApiResponse(responseCode = "409",
            description = "이미 반려되어 끝났거나, 아직 담당 평가사가 정해지지 않은 신청입니다. "
                    + "뒤쪽은 배정 대기 목록에서 수락하면 풀립니다.")
    ResponseEntity<EvaluationResultResponse> submit(
            AuthenticatedUser authenticatedUser,
            long evaluationId,
            EvaluationResultSubmitRequest request);

    @Operation(summary = "평가 결과 항목별 수정",
            description = """
                    이미 제출한 결과에서 바꾸려는 항목만 보냅니다. **보내지 않은 항목은 그대로
                    유지됩니다.** 사진 한 장을 바꾸려고 주행거리와 시세까지 다시 보낼 필요가 없습니다.

                    - `mileage` · `estimatedPrice` · `diagnosticReportUrl` — 보낸 값으로 바꿉니다.
                    - `imageUrls` — 수정 뒤의 **사진 목록 전부**입니다. 목록에서 빠진 주소는
                      삭제되고, 새 주소는 추가되며, 배열 순서가 곧 표시 순서이고 첫 번째가
                      대표 이미지가 됩니다. 한 장을 더하려면 기존 목록에 그 한 장을 더해 보내고,
                      한 장을 빼려면 그 주소만 빼고 보냅니다. **빈 배열은 보낼 수 없습니다** —
                      대표 이미지가 될 사진이 없어집니다.
                    - `keywords` — 수정 뒤의 키워드 전부입니다. 빈 배열을 보내면 전부 지워집니다.

                    새로 올리는 사진과 진단서는 제출과 마찬가지로 업로드 주소 발급 API
                    (POST /api/uploads/presigned)로 받은 fileUrl 을 보냅니다.

                    **아직 결과를 한 번도 제출하지 않은 평가는 수정할 수 없습니다.**
                    항목별 수정은 완전한 결과가 있어야 성립하므로, 처음에는 PUT 으로 전부
                    제출해야 합니다.

                    **이 신청에 배정된 평가사만 수정할 수 있습니다.** 수정해도 판매자에게 승인
                    알림이 다시 가지는 않습니다.
                    """)
    @ApiResponse(responseCode = "200", description = "보낸 항목이 반영되었습니다. 응답은 수정 뒤의 결과 전부입니다.")
    @ApiResponse(responseCode = "400",
            description = "값이 허용 범위를 벗어났거나, 우리가 발급하지 않은 주소이거나, "
                    + "바꿀 항목을 하나도 보내지 않았습니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "403", description = "다른 평가사가 담당인 신청입니다.")
    @ApiResponse(responseCode = "404", description = "없는 평가입니다.")
    @ApiResponse(responseCode = "409",
            description = "아직 결과가 제출되지 않았거나, 이미 반려되어 끝났거나, "
                    + "아직 담당 평가사가 정해지지 않은 신청입니다.")
    ResponseEntity<EvaluationResultResponse> patch(
            AuthenticatedUser authenticatedUser,
            long evaluationId,
            EvaluationResultPatchRequest request);

    @Operation(summary = "방문 결과 반려",
            description = """
                    평가사가 방문해 보니 매물로 내보낼 수 없는 차량(번호판 불일치, 심각한 사고 이력
                    등)일 때 사유를 남겨 신청을 끝냅니다. 신청 상태가 REJECTED가 되고 판매자에게
                    알림이 갑니다.

                    사유는 판매자가 신청 상세(GET /api/evaluations/{evaluationId})의 rejectReason
                    에서 확인합니다.

                    **아직 결과가 제출되지 않은 신청(REQUESTED)만 반려할 수 있습니다.** 승인으로
                    제출을 마친 뒤에는 반려로 뒤집을 수 없습니다 — 승인 알림이 이미 나갔고 판매자가
                    그 사이 경매글을 올렸을 수 있습니다. 잘못 적은 값을 고치는 것은 결과 재제출
                    (PUT /api/evaluations/{evaluationId}/result)이나 항목별 수정
                    (PATCH /api/evaluations/{evaluationId}/result)으로 합니다.

                    **이 신청에 배정된 평가사만 반려할 수 있습니다.** 배정은 배정 대기 목록에서
                    수락할 때 받습니다.

                    반려된 차량은 판매자가 같은 번호판으로 다시 방문견적을 신청할 수 있습니다.

                    PUT이 아니라 POST인 것은 반려가 되돌릴 수 없는 종료 처리이기 때문입니다.
                    같은 요청을 두 번 보내면 두 번째는 409입니다.
                    """)
    @ApiResponse(responseCode = "200", description = "반려되었습니다.")
    @ApiResponse(responseCode = "400", description = "사유가 비어 있거나 길이 상한을 넘었습니다.")
    @ApiResponse(responseCode = "401", description = "세션이 없거나 만료된 경우입니다.")
    @ApiResponse(responseCode = "403", description = "다른 평가사가 담당인 신청입니다.")
    @ApiResponse(responseCode = "404", description = "없는 평가입니다.")
    @ApiResponse(responseCode = "409",
            description = "이미 승인·반려로 끝났거나, 아직 담당 평가사가 정해지지 않은 신청입니다. "
                    + "뒤쪽은 배정 대기 목록에서 수락하면 풀립니다.")
    ResponseEntity<EvaluationRejectionResponse> reject(
            AuthenticatedUser authenticatedUser,
            long evaluationId,
            EvaluationRejectRequest request);
}
